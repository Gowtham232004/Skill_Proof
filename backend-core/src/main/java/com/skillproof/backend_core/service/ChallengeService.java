package com.skillproof.backend_core.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.CreateRepoGroundedChallengeRequest;
import com.skillproof.backend_core.dto.request.CreateChallengeRequest;
import com.skillproof.backend_core.dto.request.SubmitChallengeRequest;
import com.skillproof.backend_core.dto.response.ChallengeResponse;
import com.skillproof.backend_core.dto.response.ChallengeSubmissionResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.Challenge;
import com.skillproof.backend_core.model.ChallengeSubmission;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.ChallengeRepository;
import com.skillproof.backend_core.repository.ChallengeSubmissionRepository;
import com.skillproof.backend_core.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeService {

    private static final Set<User.Role> RECRUITER_ROLES = Set.of(
        User.Role.RECRUITER,
        User.Role.COMPANY,
        User.Role.ADMIN
    );
    private static final Pattern CODE_SUMMARY_SECTION_PATTERN = Pattern.compile(
        "--- FILE: (.+?) ---\\R(.*?)(?=\\R--- FILE: |\\z)",
        Pattern.DOTALL
    );
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]{2,}\\b");
    private static final Set<String> RESERVED_WORDS = Set.of(
        "public", "private", "protected", "class", "interface", "static", "final", "void",
        "return", "new", "import", "package", "from", "def", "function", "const", "let",
        "var", "if", "else", "for", "while", "switch", "case", "try", "catch", "true", "false",
        "null", "none", "this", "super", "extends", "implements", "string", "int", "long", "bool"
    );
    private static final int MAX_RECENT_REPO_GROUNDED = 12;

    private final ChallengeRepository challengeRepository;
    private final ChallengeSubmissionRepository challengeSubmissionRepository;
    private final UserRepository userRepository;
    private final BadgeRepository badgeRepository;
    private final DockerExecutionService dockerExecutionService;
    private final AiGatewayService aiGatewayService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChallengeResponse createChallenge(Long recruiterId, CreateChallengeRequest request) {
        User recruiter = ensureRecruiter(recruiterId);
        Integer timeLimitSeconds = request.getTimeLimitSeconds();
        if (timeLimitSeconds == null) {
            timeLimitSeconds = 10;
        }
        Challenge.AccessMode accessMode = normalizeAccessMode(request.getAccessMode(), Challenge.AccessMode.OPEN);
        List<String> assignedCandidates = normalizeAssignedCandidateUsernames(request.getAssignedCandidateUsernames());
        validateAccessAssignment(accessMode, assignedCandidates, "manual challenge");

        Challenge challenge = Challenge.builder()
            .recruiter(recruiter)
            .title(request.getTitle().trim())
            .description(request.getDescription().trim())
            .language(request.getLanguage().trim())
            .challengeMode(Challenge.ChallengeMode.MANUAL)
            .accessMode(accessMode)
            .assignedCandidatesJson(writeAssignedCandidates(assignedCandidates))
            .starterCode(request.getStarterCode())
            .referenceSolution(request.getReferenceSolution())
            .testCasesJson(writeTestCases(normalizeTestCases(request.getTestCases())))
            .timeLimitSeconds(timeLimitSeconds)
            .expiresAt(request.getExpiresAt())
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .build();

        Challenge saved = challengeRepository.save(challenge);
        notifyAssignedCandidates(saved, assignedCandidates, recruiter.getId());
        return toChallengeResponse(saved, true);
    }

    @Transactional
    public ChallengeResponse createRepoGroundedChallenge(Long recruiterId, CreateRepoGroundedChallengeRequest request) {
        User recruiter = ensureRecruiter(recruiterId);
        String badgeToken = safeTrim(request.getBadgeToken());
        if (badgeToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BADGE_TOKEN_REQUIRED", "Badge token is required");
        }

        Badge badge = badgeRepository.findByVerificationToken(badgeToken)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BADGE_NOT_FOUND", "Badge not found"));

        VerificationSession session = badge.getSession();
        List<Challenge> badgeRepoGroundedAll = challengeRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId)
            .stream()
            .filter(existing -> existing.getChallengeMode() == Challenge.ChallengeMode.REPO_GROUNDED)
            .filter(existing -> Objects.equals(existing.getSourceBadgeToken(), badgeToken))
            .collect(Collectors.toList());

        int historicalRepoGroundedCount = badgeRepoGroundedAll.size();

        List<Challenge> recentRepoGrounded = badgeRepoGroundedAll.stream()
            .limit(MAX_RECENT_REPO_GROUNDED)
            .collect(Collectors.toList());

        List<String> recentlyUsedSourcePaths = recentRepoGrounded
            .stream()
            .map(Challenge::getSourceFilePath)
            .filter(Objects::nonNull)
            .map(this::safeTrim)
            .filter(path -> !path.isBlank())
            .collect(Collectors.toList());

        List<String> recentlyUsedSnippetHashes = recentRepoGrounded
            .stream()
            .map(Challenge::getSourceSnippetHash)
            .filter(Objects::nonNull)
            .map(this::safeTrim)
            .filter(hash -> !hash.isBlank())
            .collect(Collectors.toList());

        String preferredLanguage = normalizePreferredLanguage(request.getPreferredLanguage(), session.getRepoLanguage());
        RepoSnippetSource source = selectRepoSnippet(
            session.getCodeSummary(),
            preferredLanguage,
            recentlyUsedSourcePaths,
            recentlyUsedSnippetHashes
        );
        if (source == null || source.snippet().isBlank()) {
            if (hasAnySnippetForLanguage(session.getCodeSummary(), preferredLanguage)) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REPO_GROUNDED_SOURCE_EXHAUSTED",
                    "No unused " + preferredLanguage + " snippet is currently available for this badge"
                );
            }
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "REPO_GROUNDED_LANGUAGE_UNAVAILABLE",
                "No usable " + preferredLanguage + " files were found in this badge repository summary"
            );
        }

        String language = inferLanguageFromSourceFile(source.filePath(), preferredLanguage);
        String challengeType = safeTrim(request.getChallengeType()).isBlank()
            ? "REPO_BUG_FIX"
            : safeTrim(request.getChallengeType());
        RepoChallengeDraft draft = buildRepoGroundedDraft(
            session,
            source,
            language,
            challengeType,
            historicalRepoGroundedCount
        );
        Integer timeLimitSeconds = request.getTimeLimitSeconds();
        if (timeLimitSeconds == null) {
            timeLimitSeconds = 20;
        }

        Challenge.AccessMode accessMode = normalizeAccessMode(request.getAccessMode(), Challenge.AccessMode.ASSIGNED);
        List<String> assignedCandidates = normalizeAssignedCandidateUsernames(request.getAssignedCandidateUsernames());
        if (assignedCandidates.isEmpty() && accessMode == Challenge.AccessMode.ASSIGNED) {
            assignedCandidates = List.of(session.getUser().getGithubUsername());
        }
        validateAccessAssignment(accessMode, assignedCandidates, "repo-grounded challenge");

        Challenge challenge = Challenge.builder()
            .recruiter(recruiter)
            .title(draft.title())
            .description(draft.description())
            .language(language)
            .challengeMode(Challenge.ChallengeMode.REPO_GROUNDED)
            .accessMode(accessMode)
            .assignedCandidatesJson(writeAssignedCandidates(assignedCandidates))
            .starterCode(draft.starterCode())
            .referenceSolution(draft.referenceSolution())
            .testCasesJson(writeTestCases(draft.testCases()))
            .timeLimitSeconds(timeLimitSeconds)
            .expiresAt(request.getExpiresAt())
            .sourceBadgeToken(badgeToken)
            .sourceRepoName(session.getRepoOwner() + "/" + session.getRepoName())
            .sourceFilePath(source.filePath())
            .sourceSnippetHash(source.snippetHash())
            .generationReason(draft.generationReason())
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .build();

        Challenge saved = challengeRepository.save(challenge);
        notifyAssignedCandidates(saved, assignedCandidates, recruiter.getId());
        return toChallengeResponse(saved, true);
    }

    @Transactional(readOnly = true)
    public ChallengeResponse getChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findByIdAndIsActiveTrue(challengeId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "CHALLENGE_NOT_FOUND",
                "Challenge not found"
            ));

        if (challenge.getExpiresAt() != null && LocalDateTime.now().isAfter(challenge.getExpiresAt())) {
            throw new ApiException(
                HttpStatus.GONE,
                "CHALLENGE_EXPIRED",
                "This challenge has expired"
            );
        }

        return toChallengeResponse(challenge, false);
    }

    private void notifyAssignedCandidates(Challenge challenge, List<String> assignedCandidates, Long recruiterId) {
        if (assignedCandidates == null || assignedCandidates.isEmpty()) {
            return;
        }

        for (String username : assignedCandidates) {
            notificationService.notifyChallengeAssigned(
                username,
                recruiterId,
                challenge.getId(),
                challenge.getTitle()
            );
        }
    }

    @Transactional
    public ChallengeSubmissionResponse submitChallenge(Long challengeId, Long candidateId, SubmitChallengeRequest request) {
        Challenge challenge = challengeRepository.findByIdAndIsActiveTrue(challengeId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "CHALLENGE_NOT_FOUND",
                "Challenge not found"
            ));

        if (challenge.getExpiresAt() != null && LocalDateTime.now().isAfter(challenge.getExpiresAt())) {
            throw new ApiException(
                HttpStatus.GONE,
                "CHALLENGE_EXPIRED",
                "This challenge has expired"
            );
        }

        User candidate = userRepository.findById(candidateId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User not found"
            ));

        if (!canCandidateSubmit(challenge, candidate)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "CHALLENGE_NOT_ASSIGNED",
                "This challenge is assigned to specific candidates"
            );
        }

        String sanitizedCode = sanitizeSubmittedCode(challenge.getLanguage(), request.getCode());
        if (isUnchangedFromStarter(challenge.getStarterCode(), sanitizedCode)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "CHALLENGE_CODE_NOT_MODIFIED",
                "Submitted code is unchanged from the starter template. Please edit solve() before submitting."
            );
        }

        DockerExecutionService.DockerExecutionResult executionResult = dockerExecutionService.evaluateSubmission(
            challenge.getLanguage(),
            sanitizedCode,
            challenge.getReferenceSolution(),
            readTestCases(challenge.getTestCasesJson())
        );

        ChallengeSubmission submission = ChallengeSubmission.builder()
            .challenge(challenge)
            .candidate(candidate)
            .submittedCode(sanitizedCode)
            .score(executionResult.score())
            .status(mapExecutionStatus(executionResult.status()))
            .feedback(executionResult.feedback())
            .stdout(executionResult.stdout())
            .stderr(executionResult.stderr())
            .testResultsJson(writeTestResults(executionResult.testCases()))
            .createdAt(LocalDateTime.now())
            .build();

        ChallengeSubmission saved = challengeSubmissionRepository.save(submission);
        return toSubmissionResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public List<ChallengeSubmissionResponse> getChallengeSubmissions(Long challengeId, Long recruiterId) {
        Challenge challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "CHALLENGE_NOT_FOUND",
                "Challenge not found"
            ));

        if (!Objects.equals(challenge.getRecruiter().getId(), recruiterId)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "CHALLENGE_FORBIDDEN",
                "You do not own this challenge"
            );
        }

        return challengeSubmissionRepository.findByChallengeIdOrderByCreatedAtDesc(challengeId)
            .stream()
            .map(submission -> toSubmissionResponse(submission, true))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getChallengeReferenceAnswer(Long challengeId, Long recruiterId) {
        Challenge challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "CHALLENGE_NOT_FOUND",
                "Challenge not found"
            ));

        if (!Objects.equals(challenge.getRecruiter().getId(), recruiterId)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "CHALLENGE_FORBIDDEN",
                "You do not own this challenge"
            );
        }

        String referenceSolution = safeTrim(challenge.getReferenceSolution());
        if (referenceSolution.isBlank()) {
            throw new ApiException(
                HttpStatus.NOT_FOUND,
                "CHALLENGE_REFERENCE_UNAVAILABLE",
                "Reference answer is unavailable for this challenge"
            );
        }

        return Map.of(
            "challengeId", challenge.getId(),
            "title", safeTrim(challenge.getTitle()),
            "language", safeTrim(challenge.getLanguage()),
            "referenceSolution", referenceSolution,
            "sourceFilePath", safeTrim(challenge.getSourceFilePath()),
            "generationReason", safeTrim(challenge.getGenerationReason())
        );
    }

    private User ensureRecruiter(Long recruiterId) {
        User recruiter = userRepository.findById(recruiterId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User not found"
            ));

        if (!RECRUITER_ROLES.contains(recruiter.getRole())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "RECRUITER_ROLE_REQUIRED",
                "Recruiter role required"
            );
        }

        return recruiter;
    }

    private ChallengeSubmission.SubmissionStatus mapExecutionStatus(DockerExecutionService.DockerExecutionStatus status) {
        if (status == DockerExecutionService.DockerExecutionStatus.PASSED) {
            return ChallengeSubmission.SubmissionStatus.PASSED;
        }
        if (status == DockerExecutionService.DockerExecutionStatus.FAILED) {
            return ChallengeSubmission.SubmissionStatus.FAILED;
        }
        return ChallengeSubmission.SubmissionStatus.ERROR;
    }

    private ChallengeResponse toChallengeResponse(Challenge challenge, boolean includeExpectedOutputs) {
        List<CreateChallengeRequest.TestCaseInput> cases = normalizeTestCases(readTestCases(challenge.getTestCasesJson()));
        long visibleCount = cases.stream().filter(this::isVisible).count();
        List<ChallengeResponse.TestCasePreview> previews = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            CreateChallengeRequest.TestCaseInput testCase = cases.get(i);
            if (!includeExpectedOutputs && !isVisible(testCase)) {
                continue;
            }

            previews.add(ChallengeResponse.TestCasePreview.builder()
                .caseNumber(i + 1)
                .name("Test #" + (i + 1))
                .stdin(testCase.getStdin())
                .isVisible(isVisible(testCase))
                .expectedOutput(shouldExposeExpectedOutput(testCase, includeExpectedOutputs) ? testCase.getExpectedOutput() : null)
                .build());
        }

        return ChallengeResponse.builder()
            .id(challenge.getId())
            .title(challenge.getTitle())
            .description(challenge.getDescription())
            .language(challenge.getLanguage())
            .challengeMode(challenge.getChallengeMode() == null ? Challenge.ChallengeMode.MANUAL.name() : challenge.getChallengeMode().name())
            .accessMode(challenge.getAccessMode() == null ? Challenge.AccessMode.OPEN.name() : challenge.getAccessMode().name())
            .assignedCandidateUsernames(includeExpectedOutputs ? readAssignedCandidates(challenge.getAssignedCandidatesJson()) : List.of())
            .starterCode(challenge.getStarterCode())
            .timeLimitSeconds(challenge.getTimeLimitSeconds())
            .expiresAt(challenge.getExpiresAt())
            .createdAt(challenge.getCreatedAt())
            .recruiterUsername(challenge.getRecruiter().getGithubUsername())
            .sourceBadgeToken(challenge.getSourceBadgeToken())
            .sourceRepoName(challenge.getSourceRepoName())
            .sourceFilePath(challenge.getSourceFilePath())
            .sourceSnippetHash(challenge.getSourceSnippetHash())
            .generationReason(challenge.getGenerationReason())
            .totalTestCases(cases.size())
            .visibleTestCases((int) visibleCount)
            .testCases(previews)
            .build();
    }

    private boolean canCandidateSubmit(Challenge challenge, User candidate) {
        if (challenge == null || candidate == null) {
            return false;
        }

        Challenge.AccessMode accessMode = challenge.getAccessMode() == null
            ? Challenge.AccessMode.OPEN
            : challenge.getAccessMode();
        if (accessMode == Challenge.AccessMode.OPEN) {
            return true;
        }

        String candidateUsername = safeTrim(candidate.getGithubUsername()).toLowerCase();
        if (candidateUsername.isBlank()) {
            return false;
        }

        List<String> assigned = readAssignedCandidates(challenge.getAssignedCandidatesJson());
        return assigned.contains(candidateUsername);
    }

    private ChallengeSubmissionResponse toSubmissionResponse(ChallengeSubmission submission, boolean includeHiddenExpectedOutputs) {
        List<CreateChallengeRequest.TestCaseInput> challengeCases = readTestCases(submission.getChallenge().getTestCasesJson());
        List<ChallengeSubmissionResponse.TestCaseResultDto> testCases = readTestResults(submission.getTestResultsJson())
            .stream()
            .map(result -> toTestCaseResultDto(result, challengeCases, includeHiddenExpectedOutputs))
            .collect(Collectors.toList());

        return ChallengeSubmissionResponse.builder()
            .submissionId(submission.getId())
            .challengeId(submission.getChallenge().getId())
            .candidateUsername(submission.getCandidate().getGithubUsername())
            .score(submission.getScore())
            .status(submission.getStatus())
            .feedback(submission.getFeedback())
            .stdout(submission.getStdout())
            .stderr(submission.getStderr())
            .submittedCode(submission.getSubmittedCode())
            .testCases(testCases)
            .createdAt(submission.getCreatedAt())
            .build();
    }

    private ChallengeSubmissionResponse.TestCaseResultDto toTestCaseResultDto(
            DockerExecutionService.TestCaseResult result,
            List<CreateChallengeRequest.TestCaseInput> challengeCases,
            boolean includeHiddenExpectedOutputs) {
        Integer caseNumber = result.caseNumber();
        CreateChallengeRequest.TestCaseInput sourceCase = getSourceCase(challengeCases, caseNumber);
        boolean visible = isVisible(sourceCase);

        return ChallengeSubmissionResponse.TestCaseResultDto.builder()
            .caseNumber(result.caseNumber())
            .name(result.name())
            .status(result.status())
            .isVisible(visible)
            .expectedOutput(includeHiddenExpectedOutputs || visible ? result.expectedOutput() : null)
            .actualOutput(result.actualOutput())
            .errorMessage(result.errorMessage())
            .build();
    }

    private CreateChallengeRequest.TestCaseInput getSourceCase(List<CreateChallengeRequest.TestCaseInput> cases, Integer caseNumber) {
        if (caseNumber == null || caseNumber <= 0 || cases == null || caseNumber > cases.size()) {
            return null;
        }
        return cases.get(caseNumber - 1);
    }

    private boolean shouldExposeExpectedOutput(CreateChallengeRequest.TestCaseInput testCase, boolean includeExpectedOutputs) {
        return includeExpectedOutputs || isVisible(testCase);
    }

    private boolean isVisible(CreateChallengeRequest.TestCaseInput testCase) {
        return testCase != null && Boolean.TRUE.equals(testCase.getIsVisible());
    }

    private String writeTestResults(List<DockerExecutionService.TestCaseResult> testCases) {
        try {
            return objectMapper.writeValueAsString(testCases == null ? List.of() : testCases);
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CHALLENGE_TEST_RESULTS_SERIALIZATION_FAILED",
                "Failed to store challenge test results"
            );
        }
    }

    private List<DockerExecutionService.TestCaseResult> readTestResults(String testResultsJson) {
        if (testResultsJson == null || testResultsJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(
                testResultsJson,
                new TypeReference<List<DockerExecutionService.TestCaseResult>>() {
                }
            );
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CHALLENGE_TEST_RESULTS_DESERIALIZATION_FAILED",
                "Failed to load challenge test results",
                Map.of("challengeSubmissionDataInvalid", true)
            );
        }
    }

    private String writeTestCases(List<CreateChallengeRequest.TestCaseInput> testCases) {
        try {
            return objectMapper.writeValueAsString(testCases == null ? Collections.emptyList() : normalizeTestCases(testCases));
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CHALLENGE_SERIALIZATION_FAILED",
                "Failed to store challenge test cases"
            );
        }
    }

    private String writeAssignedCandidates(List<String> usernames) {
        try {
            return objectMapper.writeValueAsString(usernames == null ? List.of() : usernames);
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CHALLENGE_ASSIGNMENT_SERIALIZATION_FAILED",
                "Failed to store challenge assignment"
            );
        }
    }

    private List<String> readAssignedCandidates(String assignedCandidatesJson) {
        if (assignedCandidatesJson == null || assignedCandidatesJson.isBlank()) {
            return List.of();
        }

        try {
            List<String> parsed = objectMapper.readValue(assignedCandidatesJson, new TypeReference<List<String>>() {
            });
            return normalizeAssignedCandidateUsernames(parsed);
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CHALLENGE_ASSIGNMENT_DESERIALIZATION_FAILED",
                "Failed to load challenge assignment"
            );
        }
    }

    private List<CreateChallengeRequest.TestCaseInput> readTestCases(String testCasesJson) {
        if (testCasesJson == null || testCasesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return normalizeTestCases(objectMapper.readValue(testCasesJson, new TypeReference<List<CreateChallengeRequest.TestCaseInput>>() {
            }));
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CHALLENGE_DESERIALIZATION_FAILED",
                "Failed to load challenge test cases",
                Map.of("challengeDataInvalid", true)
            );
        }
    }

    private List<CreateChallengeRequest.TestCaseInput> normalizeTestCases(List<CreateChallengeRequest.TestCaseInput> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            return List.of();
        }

        return testCases.stream().map(testCase -> {
            CreateChallengeRequest.TestCaseInput normalized = new CreateChallengeRequest.TestCaseInput();
            normalized.setStdin(testCase == null ? "" : safeTrim(testCase.getStdin()));
            normalized.setExpectedOutput(testCase == null ? "" : safeTrim(testCase.getExpectedOutput()));
            normalized.setIsVisible(testCase != null && Boolean.TRUE.equals(testCase.getIsVisible()));
            return normalized;
        }).collect(Collectors.toList());
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private Challenge.AccessMode normalizeAccessMode(String rawMode, Challenge.AccessMode defaultMode) {
        String mode = safeTrim(rawMode).toUpperCase();
        if (mode.isBlank()) {
            return defaultMode;
        }

        try {
            return Challenge.AccessMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACCESS_MODE", "accessMode must be OPEN or ASSIGNED");
        }
    }

    private List<String> normalizeAssignedCandidateUsernames(List<String> rawUsernames) {
        if (rawUsernames == null || rawUsernames.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String username : rawUsernames) {
            String cleaned = safeTrim(username).toLowerCase();
            if (!cleaned.isBlank()) {
                normalized.add(cleaned);
            }
        }
        return new ArrayList<>(normalized);
    }

    private void validateAccessAssignment(Challenge.AccessMode accessMode, List<String> assignedCandidates, String context) {
        if (accessMode == Challenge.AccessMode.ASSIGNED && (assignedCandidates == null || assignedCandidates.isEmpty())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ASSIGNED_CANDIDATES_REQUIRED",
                "Assigned candidates are required for " + context
            );
        }
    }

    private String sanitizeSubmittedCode(String language, String code) {
        String normalized = code == null ? "" : code
            .replace("\r\n", "\n")
            .replace("\r", "\n");

        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }

        if (!"python".equalsIgnoreCase(safeTrim(language))) {
            return normalized;
        }

        return dedentPythonSubmission(normalized);
    }

    private boolean isUnchangedFromStarter(String starterCode, String submittedCode) {
        String starter = normalizeCodeForComparison(starterCode);
        String submitted = normalizeCodeForComparison(submittedCode);
        return !starter.isBlank() && starter.equals(submitted);
    }

    private String dedentPythonSubmission(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }

        String[] lines = code.split("\\n", -1);
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            minIndent = Math.min(minIndent, indent);
        }

        if (minIndent <= 0 || minIndent == Integer.MAX_VALUE) {
            return code;
        }

        StringBuilder rebuilt = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.isBlank() && line.length() >= minIndent) {
                rebuilt.append(line.substring(minIndent));
            } else {
                rebuilt.append(line);
            }

            if (i < lines.length - 1) {
                rebuilt.append('\n');
            }
        }

        return rebuilt.toString();
    }

    private String normalizeLanguage(String repoLanguage) {
        String normalized = safeTrim(repoLanguage).toLowerCase();
        if (normalized.contains("java")) {
            return "java";
        }
        if (normalized.contains("javascript") || normalized.contains("typescript") || normalized.contains("node")) {
            return "javascript";
        }
        return "python";
    }

    private String normalizePreferredLanguage(String preferredLanguage, String repoLanguage) {
        String normalizedPreferred = safeTrim(preferredLanguage).toLowerCase();
        if (normalizedPreferred.equals("python") || normalizedPreferred.equals("javascript") || normalizedPreferred.equals("java")) {
            return normalizedPreferred;
        }
        return normalizeLanguage(repoLanguage);
    }

    private String inferLanguageFromSourceFile(String filePath, String fallbackLanguage) {
        String path = safeTrim(filePath).toLowerCase();
        if (path.endsWith(".java")) {
            return "java";
        }
        if (path.endsWith(".js") || path.endsWith(".jsx") || path.endsWith(".ts") || path.endsWith(".tsx")) {
            return "javascript";
        }
        if (path.endsWith(".py")) {
            return "python";
        }
        return safeTrim(fallbackLanguage).isBlank() ? "python" : fallbackLanguage;
    }

    private boolean isRuntimeCompatibleCode(String code, String language) {
        String normalized = safeTrim(code);
        if (normalized.isBlank()) {
            return false;
        }

        String lower = normalized.toLowerCase();
        if ("python".equals(language)) {
            return lower.contains("def solve(") && !lower.contains("import * as react") && !lower.contains("export function");
        }

        if ("javascript".equals(language)) {
            boolean hasSolve = lower.contains("function solve(") || lower.contains("const solve") || lower.contains("let solve") || lower.contains("var solve");
            boolean hasUnsupportedUiCode = lower.contains("import * as react")
                || lower.contains("react.componentprops")
                || lower.contains("useeffect(")
                || lower.contains("usestate(")
                || lower.contains(": boolean")
                || lower.contains(": string")
                || lower.contains(": number");
            return hasSolve && !hasUnsupportedUiCode;
        }

        if ("java".equals(language)) {
            return normalized.contains("class Solution") && normalized.contains("solve(");
        }

        return false;
    }

    private RepoSnippetSource selectRepoSnippet(
            String codeSummary,
            String preferredLanguage,
            List<String> recentlyUsedFilePaths,
            List<String> recentlyUsedSnippetHashes) {
        if (codeSummary == null || codeSummary.isBlank()) {
            return null;
        }

        String language = normalizePreferredLanguage(preferredLanguage, null);
        Matcher matcher = CODE_SUMMARY_SECTION_PATTERN.matcher(codeSummary);
        List<RepoSnippetSource> languageMatches = new ArrayList<>();
        while (matcher.find()) {
            String filePath = safeTrim(matcher.group(1));
            String section = safeTrim(matcher.group(2));
            String snippet = extractFocusedSnippet(section);
            if (snippet.length() < 80) {
                continue;
            }

            String snippetHash = sha256Hex(snippet);
            RepoSnippetSource candidate = new RepoSnippetSource(filePath, snippet, snippetHash);
            if (matchesLanguage(filePath, language)) {
                languageMatches.add(candidate);
            }
        }

        List<RepoSnippetSource> pool = languageMatches;
        if (pool.isEmpty()) {
            return null;
        }

        Set<String> excludedPaths = recentlyUsedFilePaths == null
            ? Set.of()
            : recentlyUsedFilePaths.stream()
                .map(this::safeTrim)
                .filter(path -> !path.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (!excludedPaths.isEmpty() && pool.size() > 1) {
            List<RepoSnippetSource> excludedFiltered = pool.stream()
                .filter(item -> !excludedPaths.contains(safeTrim(item.filePath()).toLowerCase()))
                .collect(Collectors.toList());
            if (!excludedFiltered.isEmpty()) {
                pool = excludedFiltered;
            }
        }

        Set<String> excludedHashes = recentlyUsedSnippetHashes == null
            ? Set.of()
            : recentlyUsedSnippetHashes.stream()
                .map(this::safeTrim)
                .filter(hash -> !hash.isBlank())
                .collect(Collectors.toSet());

        if (!excludedHashes.isEmpty() && pool.size() > 1) {
            List<RepoSnippetSource> hashFiltered = pool.stream()
                .filter(item -> !excludedHashes.contains(safeTrim(item.snippetHash())))
                .collect(Collectors.toList());
            if (!hashFiltered.isEmpty()) {
                pool = hashFiltered;
            }
        }

        int pick = ThreadLocalRandom.current().nextInt(pool.size());
        return pool.get(pick);
    }

    private boolean hasAnySnippetForLanguage(String codeSummary, String language) {
        if (codeSummary == null || codeSummary.isBlank()) {
            return false;
        }

        Matcher matcher = CODE_SUMMARY_SECTION_PATTERN.matcher(codeSummary);
        while (matcher.find()) {
            String filePath = safeTrim(matcher.group(1));
            if (!matchesLanguage(filePath, language)) {
                continue;
            }
            String section = safeTrim(matcher.group(2));
            String snippet = extractFocusedSnippet(section);
            if (snippet.length() >= 80) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesLanguage(String filePath, String language) {
        String path = safeTrim(filePath).toLowerCase();
        if (language.equals("java")) {
            return path.endsWith(".java");
        }
        if (language.equals("javascript")) {
            return path.endsWith(".js") || path.endsWith(".ts") || path.endsWith(".jsx") || path.endsWith(".tsx");
        }
        return path.endsWith(".py");
    }

    private String extractFocusedSnippet(String rawSection) {
        if (rawSection == null || rawSection.isBlank()) {
            return "";
        }

        int keySnippetIndex = rawSection.indexOf("Key Code Snippet:");
        String snippetSource = keySnippetIndex >= 0
            ? rawSection.substring(keySnippetIndex + "Key Code Snippet:".length()).trim()
            : rawSection;

        String[] lines = snippetSource.split("\\r?\\n");
        int endLine = Math.min(lines.length, 45);
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < endLine; i++) {
            selected.add(lines[i]);
        }
        return String.join("\n", selected).trim();
    }

    private RepoChallengeDraft buildRepoGroundedDraft(
            VerificationSession session,
            RepoSnippetSource source,
            String language,
            String challengeType,
            int recentChallengeCount) {
        List<String> identifiers = extractTopIdentifiers(source.snippet(), 6);
        if (identifiers.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "REPO_GROUNDED_IDENTIFIERS_UNAVAILABLE",
                "Could not derive stable identifiers from repository snippet"
            );
        }

        Map<String, Object> generated = aiGatewayService.generateRepoChallengeFromCode(
            source.snippet(),
            source.filePath(),
            language,
            challengeType
        );

        if (!generated.isEmpty()) {
            RepoChallengeDraft aiDraft = tryBuildAiDraft(session, source, language, generated, identifiers);
            if (aiDraft != null) {
                return aiDraft;
            }
            log.info(
                "Repo-grounded AI draft rejected; falling back to deterministic mode for repo={} file={} language={}",
                session.getRepoName(),
                source.filePath(),
                language
            );
        }

        List<String> fallbackIdentifiers = randomizeIdentifiersForFallback(identifiers);
        FallbackVariant variant = selectFallbackVariant(recentChallengeCount, source.snippetHash(), session.getRepoName());
        String reason = "Deterministic repo-grounded " + variant.reasonLabel + " from " + source.filePath() +
            " using identifiers: " + String.join(", ", fallbackIdentifiers.subList(0, Math.min(3, fallbackIdentifiers.size())));
        return new RepoChallengeDraft(
            buildRepoGroundedTitle(session, variant),
            buildRepoGroundedDescription(session, source, variant),
            buildStarterCode(language, fallbackIdentifiers, variant),
            buildReferenceSolution(language, fallbackIdentifiers, variant),
            buildRepoGroundedTestCases(fallbackIdentifiers, variant),
            reason
        );
    }

    private FallbackVariant selectFallbackVariant(int recentChallengeCount, String snippetHash, String repoName) {
        int seedOffset = Math.floorMod((safeTrim(snippetHash) + ":" + safeTrim(repoName)).hashCode(), 4);
        int mod = Math.floorMod(recentChallengeCount + seedOffset, 4);
        if (mod == 1) {
            return FallbackVariant.CASE_INSENSITIVE;
        }
        if (mod == 2) {
            return FallbackVariant.PREFIX_MATCH;
        }
        if (mod == 3) {
            return FallbackVariant.SUFFIX_MATCH;
        }
        return FallbackVariant.EXACT;
    }

    private List<String> randomizeIdentifiersForFallback(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }

        List<String> copy = new ArrayList<>(identifiers);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        int target = Math.min(6, copy.size());
        return new ArrayList<>(copy.subList(0, target));
    }

    private RepoChallengeDraft tryBuildAiDraft(
            VerificationSession session,
            RepoSnippetSource source,
            String language,
            Map<String, Object> generated,
            List<String> identifiers) {
        String starterCode = stripCodeFence(safeTrim(asString(generated.get("starter_code"))));
        List<CreateChallengeRequest.TestCaseInput> tests = parseAiTestCases(generated.get("test_cases"));
        if (starterCode.isBlank()) {
            logAiDraftReject(session, source, language, "starter_code is blank");
            return null;
        }
        if (tests.isEmpty()) {
            logAiDraftReject(session, source, language, "test_cases is empty or invalid");
            return null;
        }

        String title = safeTrim(asString(generated.get("challenge_title")));
        String description = safeTrim(asString(generated.get("challenge_description")));
        String originalCode = stripCodeFence(safeTrim(asString(generated.get("original_code"))));
        String targetFunction = safeTrim(asString(generated.get("target_function")));

        if (title.isBlank()) {
            title = "Repo-grounded Challenge: " + session.getRepoName();
        }
        if (description.isBlank()) {
            description = buildRepoGroundedDescription(session, source, FallbackVariant.EXACT);
        }
        if (originalCode.isBlank()) {
            originalCode = buildReferenceSolution(language, identifiers, FallbackVariant.EXACT);
        }

        if (!isRuntimeCompatibleCode(starterCode, language)) {
            logAiDraftReject(session, source, language, "starter_code failed runtime compatibility checks");
            return null;
        }
        if (!isRuntimeCompatibleCode(originalCode, language)) {
            originalCode = buildReferenceSolution(language, identifiers, FallbackVariant.EXACT);
            if (!isRuntimeCompatibleCode(originalCode, language)) {
                logAiDraftReject(session, source, language, "original_code incompatible even after deterministic recovery");
                return null;
            }
        }

        if (isLikelySolutionLeak(starterCode, originalCode)) {
            logAiDraftReject(session, source, language, "starter_code is too similar to original_code (solution leak)");
            return null;
        }

        String reason = "AI repo-grounded generation from " + source.filePath() +
            (targetFunction.isBlank() ? "" : (" targeting " + targetFunction));

        return new RepoChallengeDraft(title, description, starterCode, originalCode, tests, reason);
    }

    private boolean isLikelySolutionLeak(String starterCode, String referenceCode) {
        String starter = normalizeCodeForComparison(starterCode);
        String reference = normalizeCodeForComparison(referenceCode);
        if (starter.isBlank() || reference.isBlank()) {
            return false;
        }

        if (starter.equals(reference)) {
            return true;
        }

        int minLength = Math.min(starter.length(), reference.length());
        if (minLength < 120) {
            return false;
        }

        if (starter.contains(reference) || reference.contains(starter)) {
            return true;
        }

        int prefix = commonPrefixLength(starter, reference);
        int suffix = commonSuffixLength(starter, reference);
        double edgeOverlap = (prefix + suffix) / (double) minLength;
        return edgeOverlap >= 0.85;
    }

    private String normalizeCodeForComparison(String code) {
        String normalized = safeTrim(code);
        if (normalized.isBlank()) {
            return "";
        }

        String withoutBlockComments = normalized.replaceAll("(?s)/\\*.*?\\*/", "");
        String withoutSlashComments = withoutBlockComments.replaceAll("(?m)//.*$", "");
        String withoutHashComments = withoutSlashComments.replaceAll("(?m)#.*$", "");
        return withoutHashComments.replaceAll("\\s+", "");
    }

    private int commonPrefixLength(String first, String second) {
        int max = Math.min(first.length(), second.length());
        int i = 0;
        while (i < max && first.charAt(i) == second.charAt(i)) {
            i++;
        }
        return i;
    }

    private int commonSuffixLength(String first, String second) {
        int firstIndex = first.length() - 1;
        int secondIndex = second.length() - 1;
        int count = 0;
        while (firstIndex >= 0 && secondIndex >= 0 && first.charAt(firstIndex) == second.charAt(secondIndex)) {
            count++;
            firstIndex--;
            secondIndex--;
        }
        return count;
    }

    private void logAiDraftReject(
            VerificationSession session,
            RepoSnippetSource source,
            String language,
            String detail) {
        log.info(
            "Repo-grounded AI draft rejected for repo={} file={} language={} reason={}",
            session.getRepoName(),
            source.filePath(),
            language,
            detail
        );
    }

    private String stripCodeFence(String code) {
        String value = safeTrim(code);
        if (!value.startsWith("```")) {
            return value;
        }

        String[] parts = value.split("```");
        if (parts.length < 2) {
            return value;
        }

        String fenced = parts[1].trim();
        int firstNewline = fenced.indexOf('\n');
        if (firstNewline > 0) {
            String firstLine = fenced.substring(0, firstNewline).trim().toLowerCase();
            if (firstLine.matches("[a-z0-9_+-]+")) {
                return fenced.substring(firstNewline + 1).trim();
            }
        }
        return fenced;
    }

    private List<CreateChallengeRequest.TestCaseInput> parseAiTestCases(Object rawTestCases) {
        if (!(rawTestCases instanceof List<?> rows) || rows.isEmpty()) {
            return List.of();
        }

        List<CreateChallengeRequest.TestCaseInput> parsed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }

            String stdin = safeTrim(asString(firstNonNull(map, "stdin", "input")));
            String expected = safeTrim(asString(firstNonNull(map, "expectedOutput", "expected_output")));
            Object visibleRaw = firstNonNull(map, "isVisible", "is_visible");
            boolean visible = i < 2;
            if (visibleRaw instanceof Boolean) {
                visible = Boolean.TRUE.equals(visibleRaw);
            }
            if (expected.isBlank()) {
                continue;
            }

            parsed.add(createTestCase(stdin, expected, visible));
        }
        return parsed;
    }

    private Object firstNonNull(Map<?, ?> map, String first, String second) {
        Object value = map.get(first);
        return value != null ? value : map.get(second);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> extractTopIdentifiers(String snippet, int maxIdentifiers) {
        if (snippet == null || snippet.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> identifiers = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER_PATTERN.matcher(snippet);
        while (matcher.find()) {
            String token = matcher.group();
            if (RESERVED_WORDS.contains(token.toLowerCase())) {
                continue;
            }
            identifiers.add(token);
            if (identifiers.size() >= maxIdentifiers) {
                break;
            }
        }
        return new ArrayList<>(identifiers);
    }

    private List<CreateChallengeRequest.TestCaseInput> buildRepoGroundedTestCases(
            List<String> identifiers,
            FallbackVariant variant) {
        if (variant == FallbackVariant.CASE_INSENSITIVE) {
            return buildCaseInsensitiveTestCases(identifiers);
        }
        if (variant == FallbackVariant.PREFIX_MATCH) {
            return buildPrefixMatchTestCases(identifiers);
        }
        if (variant == FallbackVariant.SUFFIX_MATCH) {
            return buildSuffixMatchTestCases(identifiers);
        }
        return buildExactMatchTestCases(identifiers);
    }

    private List<CreateChallengeRequest.TestCaseInput> buildExactMatchTestCases(List<String> identifiers) {
        String first = identifiers.get(0);
        String second = identifiers.size() > 1 ? identifiers.get(1) : identifiers.get(0) + "Alt";
        String fakeOne = buildAbsentIdentifier(identifiers, "UnknownSymbol");
        String fakeTwo = buildAbsentIdentifier(identifiers, "NonExistingHandler");

        List<CreateChallengeRequest.TestCaseInput> tests = new ArrayList<>();
        tests.add(createTestCase(first, "YES", true));
        tests.add(createTestCase(fakeOne, "NO", true));
        tests.add(createTestCase(second, "YES", false));
        tests.add(createTestCase(fakeTwo, "NO", false));
        return tests;
    }

    private List<CreateChallengeRequest.TestCaseInput> buildCaseInsensitiveTestCases(List<String> identifiers) {
        String first = identifiers.get(0).toUpperCase();
        String secondBase = identifiers.size() > 1 ? identifiers.get(1) : identifiers.get(0) + "Alt";
        String second = secondBase.toLowerCase();
        String fakeOne = buildAbsentIdentifier(identifiers, "UnknownSymbol").toLowerCase();
        String fakeTwo = buildAbsentIdentifier(identifiers, "NonExistingHandler").toUpperCase();

        List<CreateChallengeRequest.TestCaseInput> tests = new ArrayList<>();
        tests.add(createTestCase(first, "YES", true));
        tests.add(createTestCase(fakeOne, "NO", true));
        tests.add(createTestCase(second, "YES", false));
        tests.add(createTestCase(fakeTwo, "NO", false));
        return tests;
    }

    private List<CreateChallengeRequest.TestCaseInput> buildPrefixMatchTestCases(List<String> identifiers) {
        String firstPrefix = buildPrefixToken(identifiers.get(0));
        String secondIdentifier = identifiers.size() > 1 ? identifiers.get(1) : identifiers.get(0);
        String secondPrefix = buildPrefixToken(secondIdentifier);
        String fakeOne = buildAbsentPrefix(identifiers, "zzz");
        String fakeTwo = buildAbsentPrefix(identifiers, "qqq");

        List<CreateChallengeRequest.TestCaseInput> tests = new ArrayList<>();
        tests.add(createTestCase(firstPrefix, "YES", true));
        tests.add(createTestCase(fakeOne, "NO", true));
        tests.add(createTestCase(secondPrefix, "YES", false));
        tests.add(createTestCase(fakeTwo, "NO", false));
        return tests;
    }

    private List<CreateChallengeRequest.TestCaseInput> buildSuffixMatchTestCases(List<String> identifiers) {
        String firstSuffix = buildSuffixToken(identifiers.get(0));
        String secondIdentifier = identifiers.size() > 1 ? identifiers.get(1) : identifiers.get(0);
        String secondSuffix = buildSuffixToken(secondIdentifier);
        String fakeOne = buildAbsentSuffix(identifiers, "zzz");
        String fakeTwo = buildAbsentSuffix(identifiers, "qqq");

        List<CreateChallengeRequest.TestCaseInput> tests = new ArrayList<>();
        tests.add(createTestCase(firstSuffix, "YES", true));
        tests.add(createTestCase(fakeOne, "NO", true));
        tests.add(createTestCase(secondSuffix, "YES", false));
        tests.add(createTestCase(fakeTwo, "NO", false));
        return tests;
    }

    private CreateChallengeRequest.TestCaseInput createTestCase(String stdin, String expectedOutput, boolean visible) {
        CreateChallengeRequest.TestCaseInput input = new CreateChallengeRequest.TestCaseInput();
        input.setStdin(stdin);
        input.setExpectedOutput(expectedOutput);
        input.setIsVisible(visible);
        return input;
    }

    private String buildAbsentIdentifier(List<String> identifiers, String seed) {
        String candidate = seed;
        int suffix = 1;
        while (identifiers.contains(candidate)) {
            candidate = seed + suffix;
            suffix++;
        }
        return candidate;
    }

    private String buildRepoGroundedTitle(VerificationSession session, FallbackVariant variant) {
        return variant.titlePrefix + ": " + session.getRepoName();
    }

    private String buildRepoGroundedDescription(
            VerificationSession session,
            RepoSnippetSource source,
            FallbackVariant variant) {
        return "Repo-grounded challenge generated from " + source.filePath() + " in " +
            session.getRepoOwner() + "/" + session.getRepoName() + ". " +
            variant.description;
    }

    private String buildStarterCode(String language, List<String> identifiers, FallbackVariant variant) {
        if ("java".equals(language)) {
            return buildJavaStarterCode(identifiers, variant);
        }
        if ("javascript".equals(language)) {
            return buildJavascriptStarterCode(identifiers, variant);
        }

        return buildPythonStarterCode(identifiers, variant);
    }

    private String buildReferenceSolution(String language, List<String> identifiers, FallbackVariant variant) {
        if ("java".equals(language)) {
            return buildJavaReferenceCode(identifiers, variant);
        }
        if ("javascript".equals(language)) {
            return buildJavascriptReferenceCode(identifiers, variant);
        }

        return buildPythonReferenceCode(identifiers, variant);
    }

    private String buildJavaStarterCode(List<String> identifiers, FallbackVariant variant) {
        if (variant == FallbackVariant.CASE_INSENSITIVE) {
            return "import java.util.Set;\n\npublic class Solution {\n  private static final Set<String> IDENTIFIERS = Set.of(" +
                identifiers.stream().map(String::toLowerCase).map(this::quoteJava).collect(Collectors.joining(", ")) +
                ");\n\n  public static String solve(String input) {\n    String token = input == null ? \"\" : input.trim().toLowerCase();\n    // TODO: return YES when token exists in IDENTIFIERS, otherwise NO.\n    return \"NO\";\n  }\n}";
        }
        if (variant == FallbackVariant.PREFIX_MATCH) {
            return "import java.util.Set;\n\npublic class Solution {\n  private static final Set<String> IDENTIFIERS = Set.of(" +
                identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
                ");\n\n  public static String solve(String input) {\n    String token = input == null ? \"\" : input.trim();\n    // TODO: return YES when token is a prefix of any identifier, otherwise NO.\n    return \"NO\";\n  }\n}";
        }
        if (variant == FallbackVariant.SUFFIX_MATCH) {
            return "import java.util.Set;\n\npublic class Solution {\n  private static final Set<String> IDENTIFIERS = Set.of(" +
                identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
                ");\n\n  public static String solve(String input) {\n    String token = input == null ? \"\" : input.trim();\n    // TODO: return YES when token is a suffix of any identifier, otherwise NO.\n    return \"NO\";\n  }\n}";
        }
        return "import java.util.Set;\n\npublic class Solution {\n  private static final Set<String> IDENTIFIERS = Set.of(" +
            identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
            ");\n\n  public static String solve(String input) {\n    String token = input == null ? \"\" : input.trim();\n    // TODO: return YES when token exists in IDENTIFIERS, otherwise NO.\n    return \"NO\";\n  }\n}";
    }

    private String buildJavaReferenceCode(List<String> identifiers, FallbackVariant variant) {
        if (variant == FallbackVariant.CASE_INSENSITIVE) {
            return "import java.util.Set;\n\npublic class Solution {\n  private static final Set<String> IDENTIFIERS = Set.of(" +
                identifiers.stream().map(String::toLowerCase).map(this::quoteJava).collect(Collectors.joining(", ")) +
                ");\n\n  public static String solve(String input) {\n    String token = input == null ? \"\" : input.trim().toLowerCase();\n    return IDENTIFIERS.contains(token) ? \"YES\" : \"NO\";\n  }\n}";
        }
        if (variant == FallbackVariant.PREFIX_MATCH) {
            return "import java.util.Set;\n\npublic class Solution {\n  private static final Set<String> IDENTIFIERS = Set.of(" +
                identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
                ");\n\n  public static String solve(String input) {\n    String token = input == null ? \"\" : input.trim();\n    boolean match = IDENTIFIERS.stream().anyMatch(id -> id.startsWith(token));\n    return match ? \"YES\" : \"NO\";\n  }\n}";
        }
        if (variant == FallbackVariant.SUFFIX_MATCH) {
            return "import java.util.Set;\n\npublic class Solution {\n  private static final Set<String> IDENTIFIERS = Set.of(" +
                identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
                ");\n\n  public static String solve(String input) {\n    String token = input == null ? \"\" : input.trim();\n    boolean match = IDENTIFIERS.stream().anyMatch(id -> id.endsWith(token));\n    return match ? \"YES\" : \"NO\";\n  }\n}";
        }
        return "import java.util.Set;\n\npublic class Solution {\n  private static final Set<String> IDENTIFIERS = Set.of(" +
            identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
            ");\n\n  public static String solve(String input) {\n    String token = input == null ? \"\" : input.trim();\n    return IDENTIFIERS.contains(token) ? \"YES\" : \"NO\";\n  }\n}";
    }

    private String buildJavascriptStarterCode(List<String> identifiers, FallbackVariant variant) {
        if (variant == FallbackVariant.CASE_INSENSITIVE) {
            return "const IDENTIFIERS = new Set([" +
                identifiers.stream().map(String::toLowerCase).map(this::quoteJava).collect(Collectors.joining(", ")) +
                "]);\n\nfunction solve(input) {\n  const token = (input ?? '').trim().toLowerCase();\n  // TODO: return 'YES' when token exists in IDENTIFIERS, otherwise 'NO'.\n  return 'NO';\n}\n\nmodule.exports = { solve };";
        }
        if (variant == FallbackVariant.PREFIX_MATCH) {
            return "const IDENTIFIERS = new Set([" +
                identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
                "]);\n\nfunction solve(input) {\n  const token = (input ?? '').trim();\n  // TODO: return 'YES' when token is a prefix of any identifier, otherwise 'NO'.\n  return 'NO';\n}\n\nmodule.exports = { solve };";
        }
        if (variant == FallbackVariant.SUFFIX_MATCH) {
            return "const IDENTIFIERS = new Set([" +
                identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
                "]);\n\nfunction solve(input) {\n  const token = (input ?? '').trim();\n  // TODO: return 'YES' when token is a suffix of any identifier, otherwise 'NO'.\n  return 'NO';\n}\n\nmodule.exports = { solve };";
        }
        return "const IDENTIFIERS = new Set([" +
            identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
            "]);\n\nfunction solve(input) {\n  const token = (input ?? '').trim();\n  // TODO: return 'YES' when token exists in IDENTIFIERS, otherwise 'NO'.\n  return 'NO';\n}\n\nmodule.exports = { solve };";
    }

    private String buildJavascriptReferenceCode(List<String> identifiers, FallbackVariant variant) {
        if (variant == FallbackVariant.CASE_INSENSITIVE) {
            return "const IDENTIFIERS = new Set([" +
                identifiers.stream().map(String::toLowerCase).map(this::quoteJava).collect(Collectors.joining(", ")) +
                "]);\n\nfunction solve(input) {\n  const token = (input ?? '').trim().toLowerCase();\n  return IDENTIFIERS.has(token) ? 'YES' : 'NO';\n}\n\nmodule.exports = { solve };";
        }
        if (variant == FallbackVariant.PREFIX_MATCH) {
            return "const IDENTIFIERS = new Set([" +
                identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
                "]);\n\nfunction solve(input) {\n  const token = (input ?? '').trim();\n  const match = [...IDENTIFIERS].some(id => id.startsWith(token));\n  return match ? 'YES' : 'NO';\n}\n\nmodule.exports = { solve };";
        }
        if (variant == FallbackVariant.SUFFIX_MATCH) {
            return "const IDENTIFIERS = new Set([" +
                identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
                "]);\n\nfunction solve(input) {\n  const token = (input ?? '').trim();\n  const match = [...IDENTIFIERS].some(id => id.endsWith(token));\n  return match ? 'YES' : 'NO';\n}\n\nmodule.exports = { solve };";
        }
        return "const IDENTIFIERS = new Set([" +
            identifiers.stream().map(this::quoteJava).collect(Collectors.joining(", ")) +
            "]);\n\nfunction solve(input) {\n  const token = (input ?? '').trim();\n  return IDENTIFIERS.has(token) ? 'YES' : 'NO';\n}\n\nmodule.exports = { solve };";
    }

    private String buildPythonStarterCode(List<String> identifiers, FallbackVariant variant) {
        if (variant == FallbackVariant.CASE_INSENSITIVE) {
            return "IDENTIFIERS = {" +
                identifiers.stream().map(String::toLowerCase).map(this::quotePython).collect(Collectors.joining(", ")) +
                "}\n\ndef solve(input_str: str) -> str:\n    token = (input_str or '').strip().lower()\n    # TODO: return 'YES' when token exists in IDENTIFIERS, otherwise 'NO'.\n    return 'NO'\n";
        }
        if (variant == FallbackVariant.PREFIX_MATCH) {
            return "IDENTIFIERS = {" +
                identifiers.stream().map(this::quotePython).collect(Collectors.joining(", ")) +
                "}\n\ndef solve(input_str: str) -> str:\n    token = (input_str or '').strip()\n    # TODO: return 'YES' when token is a prefix of any identifier, otherwise 'NO'.\n    return 'NO'\n";
        }
        if (variant == FallbackVariant.SUFFIX_MATCH) {
            return "IDENTIFIERS = {" +
                identifiers.stream().map(this::quotePython).collect(Collectors.joining(", ")) +
                "}\n\ndef solve(input_str: str) -> str:\n    token = (input_str or '').strip()\n    # TODO: return 'YES' when token is a suffix of any identifier, otherwise 'NO'.\n    return 'NO'\n";
        }
        return "IDENTIFIERS = {" +
            identifiers.stream().map(this::quotePython).collect(Collectors.joining(", ")) +
            "}\n\ndef solve(input_str: str) -> str:\n    token = (input_str or '').strip()\n    # TODO: return 'YES' when token exists in IDENTIFIERS, otherwise 'NO'.\n    return 'NO'\n";
    }

    private String buildPythonReferenceCode(List<String> identifiers, FallbackVariant variant) {
        if (variant == FallbackVariant.CASE_INSENSITIVE) {
            return "IDENTIFIERS = {" +
                identifiers.stream().map(String::toLowerCase).map(this::quotePython).collect(Collectors.joining(", ")) +
                "}\n\ndef solve(input_str: str) -> str:\n    token = (input_str or '').strip().lower()\n    return 'YES' if token in IDENTIFIERS else 'NO'\n";
        }
        if (variant == FallbackVariant.PREFIX_MATCH) {
            return "IDENTIFIERS = {" +
                identifiers.stream().map(this::quotePython).collect(Collectors.joining(", ")) +
                "}\n\ndef solve(input_str: str) -> str:\n    token = (input_str or '').strip()\n    return 'YES' if any(name.startswith(token) for name in IDENTIFIERS) else 'NO'\n";
        }
        if (variant == FallbackVariant.SUFFIX_MATCH) {
            return "IDENTIFIERS = {" +
                identifiers.stream().map(this::quotePython).collect(Collectors.joining(", ")) +
                "}\n\ndef solve(input_str: str) -> str:\n    token = (input_str or '').strip()\n    return 'YES' if any(name.endswith(token) for name in IDENTIFIERS) else 'NO'\n";
        }
        return "IDENTIFIERS = {" +
            identifiers.stream().map(this::quotePython).collect(Collectors.joining(", ")) +
            "}\n\ndef solve(input_str: str) -> str:\n    token = (input_str or '').strip()\n    return 'YES' if token in IDENTIFIERS else 'NO'\n";
    }

    private String buildPrefixToken(String identifier) {
        String token = safeTrim(identifier);
        if (token.length() <= 2) {
            return token;
        }
        return token.substring(0, 3);
    }

    private String buildSuffixToken(String identifier) {
        String token = safeTrim(identifier);
        if (token.length() <= 2) {
            return token;
        }
        return token.substring(token.length() - 3);
    }

    private String buildAbsentPrefix(List<String> identifiers, String seed) {
        String candidate = seed;
        int suffix = 1;
        while (matchesAnyIdentifierPrefix(identifiers, candidate)) {
            candidate = seed + suffix;
            suffix++;
        }
        return candidate;
    }

    private String buildAbsentSuffix(List<String> identifiers, String seed) {
        String candidate = seed;
        int suffix = 1;
        while (matchesAnyIdentifierSuffix(identifiers, candidate)) {
            candidate = seed + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean matchesAnyIdentifierPrefix(List<String> identifiers, String token) {
        for (String identifier : identifiers) {
            if (safeTrim(identifier).startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyIdentifierSuffix(List<String> identifiers, String token) {
        for (String identifier : identifiers) {
            if (safeTrim(identifier).endsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private String quoteJava(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String quotePython(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SNIPPET_HASH_UNAVAILABLE",
                "Could not compute snippet hash"
            );
        }
    }

    private record RepoSnippetSource(String filePath, String snippet, String snippetHash) {}

    private enum FallbackVariant {
        EXACT(
            "Repo-grounded Identifier Check",
            "exact-identifier check",
            "Implement solve(input) to return YES if the input token is an identifier present in the referenced code snippet; otherwise return NO."
        ),
        CASE_INSENSITIVE(
            "Repo-grounded Identifier Check (Case-Insensitive)",
            "case-insensitive identifier check",
            "Implement solve(input) to return YES if the input token matches any identifier from the referenced snippet (case-insensitive); otherwise return NO."
        ),
        PREFIX_MATCH(
            "Repo-grounded Identifier Prefix Check",
            "identifier-prefix check",
            "Implement solve(input) to return YES if the input token is a prefix of any identifier in the referenced code snippet; otherwise return NO."
        ),
        SUFFIX_MATCH(
            "Repo-grounded Identifier Suffix Check",
            "identifier-suffix check",
            "Implement solve(input) to return YES if the input token is a suffix of any identifier in the referenced code snippet; otherwise return NO."
        );

        private final String titlePrefix;
        private final String reasonLabel;
        private final String description;

        FallbackVariant(String titlePrefix, String reasonLabel, String description) {
            this.titlePrefix = titlePrefix;
            this.reasonLabel = reasonLabel;
            this.description = description;
        }
    }

    private record RepoChallengeDraft(
        String title,
        String description,
        String starterCode,
        String referenceSolution,
        List<CreateChallengeRequest.TestCaseInput> testCases,
        String generationReason
    ) {}
}
