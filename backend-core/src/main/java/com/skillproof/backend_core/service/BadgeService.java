package com.skillproof.backend_core.service;



import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.model.Answer;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.AnswerRepository;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.VerificationSessionRepository;
import com.skillproof.backend_core.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final VerificationSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final HmacUtil hmacUtil;
    private final ObjectMapper objectMapper;

    private static final String BADGE_BASE_URL = "http://localhost:3000/badge/";
    private static final int MASKED_EXCERPT_CHARS = 240;
    private static final int CODE_SNIPPET_CHARS = 3000;
    private static final int CODE_SNIPPET_LINES = 40;
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    // Create a new badge after verification completes
    public Badge createBadge(VerificationSession session,
                              Integer overallScore,
                              Integer technicalScore,
                              Integer integrityAdjustedScore,
                              Integer integrityPenaltyTotal,
                              Map<String, Integer> integrityPenaltyBreakdown,
                              Integer backendScore,
                              Integer apiDesignScore,
                              Integer errorHandlingScore,
                              Integer codeQualityScore,
                              Integer documentationScore,
                              String confidenceTier,
                              Integer tabSwitches,
                              Integer pasteCount,
                              Integer avgAnswerSeconds) {

        long timestamp = System.currentTimeMillis();
        String token = hmacUtil.generateBadgeToken(
            session.getUser().getId(),
            session.getRepoName(),
            overallScore,
            timestamp
        );

        Badge badge = Badge.builder()
            .session(session)
            .user(session.getUser())
            .verificationToken(token)
            .overallScore(overallScore)
            .technicalScore(technicalScore)
            .integrityAdjustedScore(integrityAdjustedScore)
            .integrityPenaltyTotal(integrityPenaltyTotal)
            .integrityPenaltyBreakdown(writePenaltyBreakdown(integrityPenaltyBreakdown))
            .backendScore(backendScore)
            .apiDesignScore(apiDesignScore)
            .errorHandlingScore(errorHandlingScore)
            .codeQualityScore(codeQualityScore)
            .documentationScore(documentationScore)
            .confidenceTier(confidenceTier)
            .tabSwitches(tabSwitches)
            .pasteCount(pasteCount)
            .avgAnswerSeconds(avgAnswerSeconds)
            .isActive(true)
            .build();

        badge = badgeRepository.save(badge);
        log.info("Badge created: token={}, score={}", token, overallScore);
        return badge;
    }

    // Get public badge data for the badge page
    public BadgeResponse getBadgeByToken(String token) {
        Badge badge = badgeRepository.findByVerificationToken(token)
            .orElse(null);

        if (badge == null || !badge.getIsActive()) {
            return BadgeResponse.builder()
                .valid(false)
                .verificationToken(token)
                .build();
        }

        VerificationSession session = badge.getSession();
        int repoAttemptCount = (int) sessionRepository
            .countByUserAndRepoOwnerAndRepoNameAndStatus(
                badge.getUser(),
                session.getRepoOwner(),
                session.getRepoName(),
                VerificationSession.Status.COMPLETED
            );
        int totalQuestions = (int) questionRepository.countBySessionId(session.getId());
        List<Answer> answers = answerRepository
            .findByQuestionSessionIdOrderByQuestionQuestionNumberAsc(session.getId());

        List<BadgeResponse.QuestionResultDto> questionResults = answers.stream()
            .map(this::toQuestionResult)
            .toList();

        Map<String, Integer> penaltyBreakdown = readPenaltyBreakdown(badge.getIntegrityPenaltyBreakdown());
        int technicalScore = badge.getTechnicalScore() != null
            ? badge.getTechnicalScore()
            : Objects.requireNonNullElse(badge.getOverallScore(), 0);
        int adjustedScore = badge.getIntegrityAdjustedScore() != null
            ? badge.getIntegrityAdjustedScore()
            : Objects.requireNonNullElse(badge.getOverallScore(), 0);
        int penaltyTotal = Objects.requireNonNullElse(
            badge.getIntegrityPenaltyTotal(),
            Math.max(0, technicalScore - adjustedScore)
        );

        int skippedCount = (int) answers.stream().filter(this::isSkipped).count();
        int answeredCount = Math.max(0, answers.size() - skippedCount);
        boolean evaluationComplete = answers.stream()
            .filter(answer -> !isSkipped(answer))
            .allMatch(this::hasEvaluationData);

        // Parse frameworks from JSON string
        List<String> frameworks = List.of();
        try {
            if (session.getFrameworksDetected() != null) {
                frameworks = objectMapper.readValue(
                    session.getFrameworksDetected(),
                    new TypeReference<List<String>>() {}
                );
            }
        } catch (JsonProcessingException e) {
            log.warn("Could not parse frameworks for badge {}", token);
        }

        return BadgeResponse.builder()
            .valid(true)
            .verificationToken(token)
            .badgeUrl(BADGE_BASE_URL + token)
            .githubUsername(badge.getUser().getGithubUsername())
            .avatarUrl(badge.getUser().getAvatarUrl())
            .displayName(badge.getUser().getDisplayName())
            .repoName(session.getRepoName())
            .repoOwner(session.getRepoOwner())
            .repoDescription(session.getRepoDescription())
            .primaryLanguage(session.getRepoLanguage())
            .frameworksDetected(frameworks)
            .overallScore(adjustedScore)
            .technicalScore(technicalScore)
            .integrityAdjustedScore(adjustedScore)
            .integrityPenaltyTotal(penaltyTotal)
            .integrityPenaltyBreakdown(penaltyBreakdown)
            .backendScore(badge.getBackendScore())
            .apiDesignScore(badge.getApiDesignScore())
            .errorHandlingScore(badge.getErrorHandlingScore())
            .codeQualityScore(badge.getCodeQualityScore())
            .documentationScore(badge.getDocumentationScore())
            .confidenceTier(badge.getConfidenceTier())
            .tabSwitches(badge.getTabSwitches())
            .pasteCount(badge.getPasteCount())
            .avgAnswerSeconds(badge.getAvgAnswerSeconds())
            .answeredCount(answeredCount)
            .totalQuestions(totalQuestions)
            .skippedCount(skippedCount)
            .evaluationComplete(evaluationComplete)
            .confidenceExplanation(buildConfidenceExplanation(badge, skippedCount, answeredCount, evaluationComplete))
            .answerRevealAvailable(true)
            .questionResults(questionResults)
            .repoAttemptCount(repoAttemptCount)
            .issuedAt(badge.getIssuedAt())
            .build();
    }

    private BadgeResponse.QuestionResultDto toQuestionResult(Answer answer) {
        return BadgeResponse.QuestionResultDto.builder()
            .questionNumber(answer.getQuestion().getQuestionNumber())
            .difficulty(answer.getQuestion().getDifficulty().name())
            .fileReference(answer.getQuestion().getFileReference())
            .questionText(answer.getQuestion().getQuestionText())
            .questionCodeSnippet(buildQuestionCodeSnippet(answer.getQuestion().getCodeContext()))
            .skipped(isSkipped(answer))
            .answerLength(answer.getAnswerText() == null ? 0 : answer.getAnswerText().trim().length())
            .maskedAnswerExcerpt(maskAnswer(answer.getAnswerText()))
            .accuracyScore(answer.getAccuracyScore())
            .depthScore(answer.getDepthScore())
            .specificityScore(answer.getSpecificityScore())
            .compositeScore(answer.getCompositeScore())
            .aiFeedback(answer.getAiFeedback())
            .keyPoints(buildKeyPoints(answer))
            .build();
    }

    private String buildQuestionCodeSnippet(String codeContext) {
        if (codeContext == null || codeContext.isBlank()) {
            return "";
        }

        String[] lines = codeContext.split("\\R");
        StringBuilder snippet = new StringBuilder();
        int maxLines = Math.min(lines.length, CODE_SNIPPET_LINES);
        for (int i = 0; i < maxLines; i++) {
            snippet.append(lines[i]).append(System.lineSeparator());
        }

        String raw = snippet.toString().trim();
        if (raw.length() <= CODE_SNIPPET_CHARS) {
            return raw;
        }
        return raw.substring(0, CODE_SNIPPET_CHARS) + "\n...";
    }

    private boolean isSkipped(Answer answer) {
        String text = answer.getAnswerText() == null ? "" : answer.getAnswerText().trim();
        String feedback = answer.getAiFeedback() == null ? "" : answer.getAiFeedback().toLowerCase(Locale.ROOT);
        boolean zeroedScores = Integer.valueOf(0).equals(answer.getAccuracyScore())
            && Integer.valueOf(0).equals(answer.getDepthScore())
            && Integer.valueOf(0).equals(answer.getSpecificityScore());
        return text.isBlank() && (feedback.contains("skipped") || zeroedScores);
    }

    private boolean hasEvaluationData(Answer answer) {
        return answer.getAccuracyScore() != null
            && answer.getDepthScore() != null
            && answer.getSpecificityScore() != null
            && answer.getCompositeScore() != null
            && answer.getAiFeedback() != null
            && !answer.getAiFeedback().isBlank();
    }

    private String maskAnswer(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return "";
        }
        String trimmed = answerText.trim();
        if (trimmed.length() <= MASKED_EXCERPT_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MASKED_EXCERPT_CHARS) + "...";
    }

    private List<String> buildKeyPoints(Answer answer) {
        if (isSkipped(answer)) {
            return List.of("Question was skipped by candidate.");
        }

        List<String> points = new java.util.ArrayList<>();
        if (answer.getAccuracyScore() != null && answer.getDepthScore() != null && answer.getSpecificityScore() != null) {
            int accuracy = answer.getAccuracyScore();
            int depth = answer.getDepthScore();
            int specificity = answer.getSpecificityScore();
            points.add(String.format(
                "Rubric scores - accuracy: %d/10, depth: %d/10, specificity: %d/10.",
                accuracy,
                depth,
                specificity
            ));
        }

        String feedback = answer.getAiFeedback();
        if (feedback != null && !feedback.isBlank()) {
            String[] sentences = SENTENCE_SPLIT.split(feedback.trim());
            for (String sentence : sentences) {
                if (!sentence.isBlank()) {
                    points.add(sentence.trim());
                }
                if (points.size() >= 3) {
                    break;
                }
            }
        }

        if (points.isEmpty()) {
            points.add("No detailed AI commentary available for this answer.");
        }
        return points;
    }

    private String buildConfidenceExplanation(Badge badge,
                                              int skippedCount,
                                              int answeredCount,
                                              boolean evaluationComplete) {
        String tier = badge.getConfidenceTier() == null ? "Unknown" : badge.getConfidenceTier();
        int tabSwitches = Objects.requireNonNullElse(badge.getTabSwitches(), 0);
        int pasteCount = Objects.requireNonNullElse(badge.getPasteCount(), 0);
        int avgAnswerSeconds = Objects.requireNonNullElse(badge.getAvgAnswerSeconds(), 0);
        return String.format(
            "%s confidence based on %d answered, %d skipped, tab switches=%d, paste count=%d, avg answer time=%ds, evaluation complete=%s.",
            tier,
            answeredCount,
            skippedCount,
            tabSwitches,
            pasteCount,
            avgAnswerSeconds,
            evaluationComplete ? "yes" : "no"
        );
    }

    private String writePenaltyBreakdown(Map<String, Integer> integrityPenaltyBreakdown) {
        if (integrityPenaltyBreakdown == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(integrityPenaltyBreakdown);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize integrity penalty breakdown");
            return "{}";
        }
    }

    private Map<String, Integer> readPenaltyBreakdown(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Integer>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Could not parse integrity penalty breakdown");
            return Map.of();
        }
    }
}