package com.skillproof.backend_core.service;


import java.util.ArrayList;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.StartVerificationRequest;
import com.skillproof.backend_core.dto.response.QuestionDto;
import com.skillproof.backend_core.dto.response.VerificationStartResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.UserRepository;
import com.skillproof.backend_core.repository.VerificationSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final GitHubService gitHubService;
    private final FileFilterService fileFilterService;
    private final CodeExtractorService codeExtractorService;
    private final UserRepository userRepository;
    private final VerificationSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final AiGatewayService aiGatewayService;

    @Value("${testing.bypass-verification-limit:false}")
    private boolean bypassVerificationLimit;

    @Value("${testing.skip-repo-cooldown:false}")
    private boolean skipRepoCooldown;

    @Value("${phase2.mixed-questions-enabled:false}")
    private boolean mixedQuestionsEnabled;

    @Value("${phase2.total-questions:5}")
    private int phase2TotalQuestions;

    @Value("${phase2.conceptual-questions:0}")
    private int phase2ConceptualQuestions;

    // Free plan limit (raised for current testing/submission cycle)
    private static final int FREE_MONTHLY_LIMIT = 30;
    private static final long REPO_COOLDOWN_HOURS = 24;
    private static final int MAX_SNIPPET_LINES = 15;
    private static final int MAX_SNIPPET_CHARS = 1800;

    @Transactional
    public VerificationStartResponse startVerification(Long userId,
                                                        StartVerificationRequest req) {

        // 1. Load user and check free tier limit
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User not found: " + userId
            ));

        // Skip limit check if testing mode is enabled
        if (!bypassVerificationLimit && user.getPlan() == User.Plan.FREE) {
            long completedThisMonth = sessionRepository
                .countByUserAndStatus(user, VerificationSession.Status.COMPLETED);
            if (completedThisMonth >= FREE_MONTHLY_LIMIT) {
                throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FREE_PLAN_LIMIT_REACHED",
                    "Free plan limit reached (" + FREE_MONTHLY_LIMIT
                        + " verifications/month). Please upgrade to Pro.",
                    Map.of("monthlyLimit", FREE_MONTHLY_LIMIT)
                );
            }
        }

        if (bypassVerificationLimit && user.getPlan() == User.Plan.FREE) {
            log.info("⚠️  TEST MODE: Bypassing verification limit for user {}", user.getGithubUsername());
        }

        boolean cooldownBypassed = skipRepoCooldown || bypassVerificationLimit;
        if (!cooldownBypassed) {
            sessionRepository
                .findTopByUserAndRepoOwnerIgnoreCaseAndRepoNameIgnoreCaseAndStatusOrderByStartedAtDesc(
                    user,
                    req.getRepoOwner(),
                    req.getRepoName(),
                    VerificationSession.Status.COMPLETED)
                .ifPresent(lastSession -> {
                    LocalDateTime referenceTime = lastSession.getStartedAt() != null
                        ? lastSession.getStartedAt()
                        : lastSession.getCompletedAt();

                    if (referenceTime == null) {
                        log.warn("Completed session {} has no timestamp; skipping cooldown check", lastSession.getId());
                        return;
                    }

                    LocalDateTime nextAllowedAt = referenceTime.plusHours(REPO_COOLDOWN_HOURS);
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isBefore(nextAllowedAt)) {
                        Duration remaining = Duration.between(now, nextAllowedAt);
                        long hours = remaining.toHours();
                        long minutes = remaining.minusHours(hours).toMinutes();
                        long remainingMinutes = Math.max(1, remaining.toMinutes());
                        String waitTime = hours > 0
                            ? hours + "h " + minutes + "m"
                            : minutes + "m";

                        throw new ApiException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "COOLDOWN_ACTIVE",
                            "Cooldown active for this repository. Try again in " + waitTime + ".",
                            Map.of(
                                "repoOwner", req.getRepoOwner(),
                                "repoName", req.getRepoName(),
                                "remainingMinutes", remainingMinutes,
                                "retryAt", nextAllowedAt.toString()
                            )
                        );
                    }
                });
        } else {
            log.info(
                "⚠️  TEST MODE: Bypassing repository cooldown for {}/{} (skipRepoCooldown={}, bypassVerificationLimit={})",
                req.getRepoOwner(),
                req.getRepoName(),
                skipRepoCooldown,
                bypassVerificationLimit
            );
        }

        log.info("Starting verification for user {} on repo {}/{}",
            user.getGithubUsername(), req.getRepoOwner(), req.getRepoName());

        // 2. Get repo metadata from GitHub
        Map<String, Object> repoInfo = gitHubService.getRepositoryInfo(
            user.getGithubAccessToken(), req.getRepoOwner(), req.getRepoName()
        );
        String description = (String) repoInfo.getOrDefault("description", "");
        String language    = (String) repoInfo.getOrDefault("language", "Unknown");

        // 3. Fetch full file tree
        List<String> allFiles = gitHubService.getRepositoryFileTree(
            user.getGithubAccessToken(), req.getRepoOwner(), req.getRepoName()
        );

        // 4. Filter to relevant source files only
        List<String> relevantFiles = fileFilterService.filterAndRankFiles(allFiles);
        log.info("Filtered to {} relevant files from {}", relevantFiles.size(), allFiles.size());

        // 5. Fetch file contents
        Map<String, String> fileContents = gitHubService.fetchFileContents(
            user.getGithubAccessToken(), req.getRepoOwner(), req.getRepoName(), relevantFiles
        );

        // 6. Detect frameworks
        List<String> frameworks = fileFilterService.detectFrameworks(
            new ArrayList<>(fileContents.keySet()), fileContents
        );
        String primaryLanguage = fileFilterService.detectPrimaryLanguage(allFiles);

        // 7. Extract code structure summary
        String codeSummary = codeExtractorService.extractCodeSummary(
            fileContents, primaryLanguage
        );

        // 8. Save verification session to DB
        VerificationSession session = VerificationSession.builder()
            .user(user)
            .repoOwner(req.getRepoOwner())
            .repoName(req.getRepoName())
            .repoDescription(description)
            .repoLanguage(language)
            .frameworksDetected(toJson(frameworks))
            .filesAnalyzed(fileContents.size())
            .codeSummary(codeSummary)
            .status(VerificationSession.Status.IN_PROGRESS)
            .build();
        session = sessionRepository.save(session);

        log.info("Session {} created. Code summary: {} chars. Now calling AI service...",
            session.getId(), codeSummary.length());

        // 9. Call AI service to generate questions
        int totalQuestions = mixedQuestionsEnabled
            ? Math.max(5, Math.min(phase2TotalQuestions, 7))
            : 5;
        int conceptualQuestions = mixedQuestionsEnabled
            ? Math.max(0, Math.min(phase2ConceptualQuestions, totalQuestions - 1))
            : 0;

        List<Question> questions = aiGatewayService.generateQuestionsViaAI(
            session, 
            codeSummary, 
            primaryLanguage, 
            frameworks, 
            fileContents,
            totalQuestions,
            conceptualQuestions);

        questionRepository.saveAll(questions);

        // 10. Build response
        return VerificationStartResponse.builder()
            .sessionId(session.getId())
            .repoName(req.getRepoName())
            .repoOwner(req.getRepoOwner())
            .repoDescription(description)
            .primaryLanguage(primaryLanguage)
            .frameworksDetected(frameworks)
            .filesAnalyzed(fileContents.size())
            .questions(questions.stream().map(this::toQuestionDto).collect(Collectors.toList()))
            .status("IN_PROGRESS")
            .build();
    }

    private QuestionDto toQuestionDto(Question q) {
        return QuestionDto.builder()
            .id(q.getId())
            .questionNumber(q.getQuestionNumber())
            .difficulty(q.getDifficulty().name())
            .questionType(
                q.getQuestionType() != null
                    ? q.getQuestionType().name()
                    : Question.QuestionType.CODE_GROUNDED.name()
            )
            .fileReference(q.getFileReference())
            .questionText(q.getQuestionText())
            .codeContextSnippet(buildCodeContextSnippet(q.getCodeContext()))
            .build();
    }

    private String buildCodeContextSnippet(String codeContext) {
        if (codeContext == null || codeContext.isBlank()) {
            return "";
        }

        String[] lines = codeContext.split("\\R");
        StringBuilder snippet = new StringBuilder();
        int limit = Math.min(lines.length, MAX_SNIPPET_LINES);
        for (int i = 0; i < limit; i++) {
            snippet.append(lines[i]).append(System.lineSeparator());
        }

        String result = snippet.toString().trim();
        if (result.length() > MAX_SNIPPET_CHARS) {
            return result.substring(0, MAX_SNIPPET_CHARS) + "\n...";
        }
        return result;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}