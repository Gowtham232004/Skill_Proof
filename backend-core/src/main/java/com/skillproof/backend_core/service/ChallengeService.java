package com.skillproof.backend_core.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.CreateChallengeRequest;
import com.skillproof.backend_core.dto.request.SubmitChallengeRequest;
import com.skillproof.backend_core.dto.response.ChallengeResponse;
import com.skillproof.backend_core.dto.response.ChallengeSubmissionResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Challenge;
import com.skillproof.backend_core.model.ChallengeSubmission;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.ChallengeRepository;
import com.skillproof.backend_core.repository.ChallengeSubmissionRepository;
import com.skillproof.backend_core.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private static final Set<User.Role> RECRUITER_ROLES = Set.of(
        User.Role.RECRUITER,
        User.Role.COMPANY,
        User.Role.ADMIN
    );

    private final ChallengeRepository challengeRepository;
    private final ChallengeSubmissionRepository challengeSubmissionRepository;
    private final UserRepository userRepository;
    private final DockerExecutionService dockerExecutionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChallengeResponse createChallenge(Long recruiterId, CreateChallengeRequest request) {
        User recruiter = ensureRecruiter(recruiterId);
        Integer timeLimitSeconds = request.getTimeLimitSeconds();
        if (timeLimitSeconds == null) {
            timeLimitSeconds = 10;
        }

        Challenge challenge = Challenge.builder()
            .recruiter(recruiter)
            .title(request.getTitle().trim())
            .description(request.getDescription().trim())
            .language(request.getLanguage().trim())
            .starterCode(request.getStarterCode())
            .referenceSolution(request.getReferenceSolution())
            .testCasesJson(writeTestCases(request.getTestCases()))
            .timeLimitSeconds(timeLimitSeconds)
            .expiresAt(request.getExpiresAt())
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .build();

        Challenge saved = challengeRepository.save(challenge);
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

        DockerExecutionService.DockerExecutionResult executionResult = dockerExecutionService.evaluateSubmission(
            challenge.getLanguage(),
            request.getCode(),
            challenge.getReferenceSolution(),
            readTestCases(challenge.getTestCasesJson())
        );

        ChallengeSubmission submission = ChallengeSubmission.builder()
            .challenge(challenge)
            .candidate(candidate)
            .submittedCode(request.getCode())
            .score(executionResult.score())
            .status(mapExecutionStatus(executionResult.status()))
            .feedback(executionResult.feedback())
            .stdout(executionResult.stdout())
            .stderr(executionResult.stderr())
            .testResultsJson(writeTestResults(executionResult.testCases()))
            .createdAt(LocalDateTime.now())
            .build();

        ChallengeSubmission saved = challengeSubmissionRepository.save(submission);
        return toSubmissionResponse(saved);
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
            .map(this::toSubmissionResponse)
            .collect(Collectors.toList());
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
        List<CreateChallengeRequest.TestCaseInput> cases = readTestCases(challenge.getTestCasesJson());
        List<ChallengeResponse.TestCasePreview> previews = cases.stream()
            .map(testCase -> ChallengeResponse.TestCasePreview.builder()
                .stdin(testCase.getStdin())
                .expectedOutput(includeExpectedOutputs ? testCase.getExpectedOutput() : null)
                .build())
            .collect(Collectors.toList());

        return ChallengeResponse.builder()
            .id(challenge.getId())
            .title(challenge.getTitle())
            .description(challenge.getDescription())
            .language(challenge.getLanguage())
            .starterCode(challenge.getStarterCode())
            .timeLimitSeconds(challenge.getTimeLimitSeconds())
            .expiresAt(challenge.getExpiresAt())
            .createdAt(challenge.getCreatedAt())
            .recruiterUsername(challenge.getRecruiter().getGithubUsername())
            .testCases(previews)
            .build();
    }

    private ChallengeSubmissionResponse toSubmissionResponse(ChallengeSubmission submission) {
        List<ChallengeSubmissionResponse.TestCaseResultDto> testCases = readTestResults(submission.getTestResultsJson())
            .stream()
            .map(result -> ChallengeSubmissionResponse.TestCaseResultDto.builder()
                .caseNumber(result.caseNumber())
                .name(result.name())
                .status(result.status())
                .expectedOutput(result.expectedOutput())
                .actualOutput(result.actualOutput())
                .errorMessage(result.errorMessage())
                .build())
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
            return objectMapper.writeValueAsString(testCases == null ? Collections.emptyList() : testCases);
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CHALLENGE_SERIALIZATION_FAILED",
                "Failed to store challenge test cases"
            );
        }
    }

    private List<CreateChallengeRequest.TestCaseInput> readTestCases(String testCasesJson) {
        if (testCasesJson == null || testCasesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(testCasesJson, new TypeReference<List<CreateChallengeRequest.TestCaseInput>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CHALLENGE_DESERIALIZATION_FAILED",
                "Failed to load challenge test cases",
                Map.of("challengeDataInvalid", true)
            );
        }
    }
}
