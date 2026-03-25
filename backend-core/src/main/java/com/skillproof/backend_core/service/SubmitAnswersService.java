package com.skillproof.backend_core.service;



import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillproof.backend_core.dto.request.SubmitAnswersRequest;
import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.dto.response.VerificationResultResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Answer;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.AnswerRepository;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.VerificationSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmitAnswersService {

    private final VerificationSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AiGatewayService aiGatewayService;
    private final BadgeService badgeService;
    private final GapAnalyzerService gapAnalyzerService;

    private static final String BADGE_BASE_URL = "http://localhost:3000/badge/";
    private static final int MIN_ANSWER_LENGTH = 20;

    @Transactional
    public VerificationResultResponse submitAnswers(Long userId,
                                                     SubmitAnswersRequest request) {

        // 1. Load and validate session
        VerificationSession session = sessionRepository.findById(request.getSessionId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "SESSION_NOT_FOUND",
                "Session not found: " + request.getSessionId()
            ));

        if (!session.getUser().getId().equals(userId)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "SESSION_FORBIDDEN",
                "Session does not belong to this user"
            );
        }

        if (session.getStatus() == VerificationSession.Status.COMPLETED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "SESSION_ALREADY_COMPLETED",
                "Session already completed"
            );
        }

        log.info("Processing answer submission for session {} user {}",
            request.getSessionId(), userId);

        // 2. Load questions for this session
        List<Question> questions = questionRepository
            .findBySessionOrderByQuestionNumber(session);

        if (questions.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "QUESTIONS_NOT_FOUND",
                "No questions found for session " + request.getSessionId()
            );
        }

        // 3. Save answers to database
        Map<Long, Question> questionMap = questions.stream()
            .collect(Collectors.toMap(Question::getId, q -> q));

        List<Answer> savedAnswers = new ArrayList<>();
        Set<Integer> skippedQuestionNumbers = new HashSet<>();
        for (SubmitAnswersRequest.AnswerItem item : request.getAnswers()) {
            Question question = questionMap.get(item.getQuestionId());
            if (question == null) {
                log.warn("Question {} not found for session {}, skipping",
                    item.getQuestionId(), request.getSessionId());
                continue;
            }

            boolean skipped = Boolean.TRUE.equals(item.getSkipped());
            String answerText = item.getAnswerText() != null ? item.getAnswerText().trim() : "";

            if (skipped) {
                skippedQuestionNumbers.add(question.getQuestionNumber());
                answerText = "";
            } else if (answerText.length() < MIN_ANSWER_LENGTH) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ANSWER_TOO_SHORT",
                    "Answer for question " + question.getQuestionNumber()
                        + " is too short. Minimum " + MIN_ANSWER_LENGTH
                        + " characters or use skip.",
                    Map.of(
                        "questionNumber", question.getQuestionNumber(),
                        "minChars", MIN_ANSWER_LENGTH,
                        "actualChars", answerText.length()
                    )
                );
            }

            Answer answer = Answer.builder()
                .question(question)
                .answerText(answerText)
                .build();
            savedAnswers.add(answerRepository.save(answer));
        }

        log.info("Saved {} answers for session {}", savedAnswers.size(),
            request.getSessionId());

        // 4. Call AI service to evaluate answers
        List<Map<String, Object>> answersForAi = buildAiEvaluationPayload(
            savedAnswers, skippedQuestionNumbers);

        Map<String, Object> evaluationResult = aiGatewayService.evaluateAnswers(
            session.getId(), answersForAi, session.getRepoLanguage()
        );

        validateEvaluationCompleteness(evaluationResult, answersForAi.size(), request.getSessionId());

        // 5. Update saved answers with AI scores and extract evaluation results
        List<BadgeResponse.QuestionResultDto> questionResults =
            updateAnswersWithScores(savedAnswers, evaluationResult, skippedQuestionNumbers);

        // 6. Compute individual skill scores from answer rubric scores
        // Map: Q1→backend, Q2→api, Q3→error, Q4→quality, Q5→documentation
        int total = 0;
        int backendRaw = 0, apiRaw = 0, errorRaw = 0, qualityRaw = 0, docsRaw = 0;

        for (Answer answer : savedAnswers) {
            Integer accuracyVal = answer.getAccuracyScore();
            Integer depthVal = answer.getDepthScore();
            Integer specificityVal = answer.getSpecificityScore();
            int accuracy = accuracyVal != null ? accuracyVal : 0;
            int depth = depthVal != null ? depthVal : 0;
            int specificity = specificityVal != null ? specificityVal : 0;
            int questionScore = (accuracy * 4 + depth * 3 + specificity * 3) / 10;
            total += questionScore;

            switch (answer.getQuestion().getQuestionNumber()) {
                case 1 -> backendRaw = questionScore;
                case 2 -> apiRaw = questionScore;
                case 3 -> errorRaw = questionScore;
                case 4 -> qualityRaw = questionScore;
                case 5 -> docsRaw = questionScore;
            }
        }

        // Convert from 1-10 scale to 0-100 scale
        int overallScore = savedAnswers.isEmpty() ? 0 : (total * 10) / savedAnswers.size();
        int backendScore     = backendRaw * 10;
        int apiScore         = apiRaw * 10;
        int errorScore       = errorRaw * 10;
        int qualityScore     = qualityRaw * 10;
        int documentScore    = docsRaw * 10;

        int skipCount = skippedQuestionNumbers.size();
        int avgAnswerLength = computeAverageAnswerLength(savedAnswers, skippedQuestionNumbers);
        int scoreSpread = computeScoreSpread(savedAnswers);
        String confidenceTier = ConfidenceTierCalculator.computeTier(
            skipCount,
            avgAnswerLength,
            scoreSpread,
            true
        );

        Integer tabSwitches = Objects.requireNonNullElse(request.getTotalTabSwitches(), 0);
        Integer pasteCount = Objects.requireNonNullElse(request.getPasteCount(), 0);
        Integer avgAnswerSeconds = Objects.requireNonNullElse(request.getAvgAnswerSeconds(), 0);

        Map<String, Integer> integrityPenaltyBreakdown = computeIntegrityPenaltyBreakdown(
            tabSwitches,
            pasteCount,
            avgAnswerSeconds
        );
        int integrityPenaltyTotal = integrityPenaltyBreakdown.values().stream()
            .mapToInt(Integer::intValue)
            .sum();
        int integrityAdjustedScore = Math.max(0, overallScore - integrityPenaltyTotal);
        
        log.info("Skill scores computed: backend={}, api={}, error={}, quality={}, docs={}", 
            backendScore, apiScore, errorScore, qualityScore, documentScore);

        // 7. Create badge with properly distributed skill scores
        Badge badge = badgeService.createBadge(
            session,
            integrityAdjustedScore,
            overallScore,
            integrityAdjustedScore,
            integrityPenaltyTotal,
            integrityPenaltyBreakdown,
            backendScore,
            apiScore,
            errorScore, qualityScore, documentScore,
            confidenceTier, tabSwitches, pasteCount, avgAnswerSeconds
        );

        // Trigger gap analysis in background (non-blocking)
        String codeContent = session.getCodeSummary() != null ? session.getCodeSummary() : "";
        gapAnalyzerService.analyzeAsync(session.getUser(), session, codeContent);

        // 8. Mark session as completed
        session.setStatus(VerificationSession.Status.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        long repoAttemptCount = sessionRepository
            .countByUserAndRepoOwnerAndRepoNameAndStatus(
                session.getUser(),
                session.getRepoOwner(),
                session.getRepoName(),
                VerificationSession.Status.COMPLETED
            );

        log.info("Verification complete. Session={} Score={} Badge={}",
            session.getId(), integrityAdjustedScore, badge.getVerificationToken());

        // 9. Build response
        return VerificationResultResponse.builder()
            .sessionId(session.getId())
            .overallScore(integrityAdjustedScore)
            .technicalScore(overallScore)
            .integrityAdjustedScore(integrityAdjustedScore)
            .integrityPenaltyTotal(integrityPenaltyTotal)
            .integrityPenaltyBreakdown(integrityPenaltyBreakdown)
            .backendScore(backendScore)
            .apiDesignScore(apiScore)
            .errorHandlingScore(errorScore)
            .codeQualityScore(qualityScore)
            .documentationScore(documentScore)
            .questionResults(questionResults)
            .badgeToken(badge.getVerificationToken())
            .badgeUrl(BADGE_BASE_URL + badge.getVerificationToken())
            .topGaps(identifyTopGaps(backendScore, apiScore, errorScore,
                qualityScore, documentScore))
            .repoAttemptCount((int) repoAttemptCount)
            .confidenceTier(confidenceTier)
            .tabSwitches(tabSwitches)
            .pasteCount(pasteCount)
            .avgAnswerSeconds(avgAnswerSeconds)
            .status("COMPLETED")
            .build();
    }

    private Map<String, Integer> computeIntegrityPenaltyBreakdown(int tabSwitches,
                                                                  int pasteCount,
                                                                  int avgAnswerSeconds) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();

        int pastePenalty = Math.min(6, Math.max(0, pasteCount) * 2);

        int speedPenalty = 0;
        if (avgAnswerSeconds > 0 && avgAnswerSeconds < 30) {
            speedPenalty = 4;
        } else if (avgAnswerSeconds >= 30 && avgAnswerSeconds < 45) {
            speedPenalty = 2;
        }

        int tabPenalty = 0;
        if (tabSwitches > 3) {
            tabPenalty = 2;
        } else if (tabSwitches > 0) {
            tabPenalty = 1;
        }

        int total = pastePenalty + speedPenalty + tabPenalty;
        if (total > 10) {
            int overflow = total - 10;
            if (tabPenalty >= overflow) {
                tabPenalty -= overflow;
            } else if (speedPenalty >= overflow - tabPenalty) {
                speedPenalty -= (overflow - tabPenalty);
                tabPenalty = 0;
            } else {
                pastePenalty = Math.max(0, pastePenalty - (overflow - tabPenalty - speedPenalty));
                speedPenalty = 0;
                tabPenalty = 0;
            }
        }

        breakdown.put("pastePenalty", pastePenalty);
        breakdown.put("speedPenalty", speedPenalty);
        breakdown.put("tabSwitchPenalty", tabPenalty);
        return breakdown;
    }

    // Build payload for AI evaluation
    private List<Map<String, Object>> buildAiEvaluationPayload(
            List<Answer> answers, Set<Integer> skippedQuestionNumbers) {

        List<Map<String, Object>> payload = new ArrayList<>();
        for (Answer answer : answers) {
            Question question = answer.getQuestion();
            if (skippedQuestionNumbers.contains(question.getQuestionNumber())) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("question_id", question.getQuestionNumber());  // Use question number (1-5), not answer DB ID
            item.put("question_text", question.getQuestionText());
            item.put("file_reference", question.getFileReference());
            item.put("code_context",
                question.getCodeContext() != null ? question.getCodeContext() : "");
            item.put("answer_text", answer.getAnswerText());
            payload.add(item);
        }
        return payload;
    }

    // Update Answer entities with scores from AI, build result DTOs
    private List<BadgeResponse.QuestionResultDto> updateAnswersWithScores(
            List<Answer> answers, Map<String, Object> evaluationResult,
            Set<Integer> skippedQuestionNumbers) {

        List<BadgeResponse.QuestionResultDto> results = new ArrayList<>();
        List<?> aiResults = (List<?>) evaluationResult.getOrDefault("results", List.of());

        Map<Object, Map<?, ?>> resultsByQuestionId = new HashMap<>();
        for (Object r : aiResults) {
            if (r instanceof Map) {
                Map<?, ?> resultMap = (Map<?, ?>) r;
                resultsByQuestionId.put(resultMap.get("question_id"), resultMap);
            }
        }

        for (Answer answer : answers) {
            int questionNumber = answer.getQuestion().getQuestionNumber();
            if (skippedQuestionNumbers.contains(questionNumber)) {
                answer.setAccuracyScore(0);
                answer.setDepthScore(0);
                answer.setSpecificityScore(0);
                answer.setCompositeScore(0.0);
                answer.setAiFeedback("Question skipped by developer.");
                answerRepository.save(answer);

                Question q = answer.getQuestion();
                results.add(BadgeResponse.QuestionResultDto.builder()
                    .questionNumber(q.getQuestionNumber())
                    .difficulty(q.getDifficulty().name())
                    .fileReference(q.getFileReference())
                    .questionText(q.getQuestionText())
                    .accuracyScore(0)
                    .depthScore(0)
                    .specificityScore(0)
                    .compositeScore(0.0)
                    .aiFeedback("Question skipped by developer.")
                    .build());
                continue;
            }

            // AI returns question_id as question number (1-5), so lookup must use the same key.
            Map<?, ?> aiResult = resultsByQuestionId.get(questionNumber);
            if (aiResult == null) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_EVALUATION_INCOMPLETE",
                    "Missing evaluation for question " + questionNumber + ".",
                    Map.of("sessionId", answer.getQuestion().getSession().getId(), "questionNumber", questionNumber)
                );
            }

            int accuracy = requireScore(aiResult.get("accuracy_score"), questionNumber, "accuracy_score");
            int depth = requireScore(aiResult.get("depth_score"), questionNumber, "depth_score");
            int specificity = requireScore(aiResult.get("specificity_score"), questionNumber, "specificity_score");
            double composite = requireCompositeScore(aiResult.get("composite_score"), questionNumber);
            String feedback = extractFeedback(aiResult, questionNumber);

            // Save scores to Answer entity
            answer.setAccuracyScore(accuracy);
            answer.setDepthScore(depth);
            answer.setSpecificityScore(specificity);
            answer.setCompositeScore(composite);
            answer.setAiFeedback(feedback);
            answerRepository.save(answer);

            // Build result DTO
            Question q = answer.getQuestion();
            results.add(BadgeResponse.QuestionResultDto.builder()
                .questionNumber(q.getQuestionNumber())
                .difficulty(q.getDifficulty().name())
                .fileReference(q.getFileReference())
                .questionText(q.getQuestionText())
                .accuracyScore(accuracy)
                .depthScore(depth)
                .specificityScore(specificity)
                .compositeScore(composite)
                .aiFeedback(feedback)
                .build());
        }

        results.sort(Comparator.comparing(BadgeResponse.QuestionResultDto::getQuestionNumber));
        return results;
    }

    // Identify top 3 weakest skill areas
    private List<String> identifyTopGaps(int backend, int api, int error,
                                          int quality, int docs) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("Backend Logic", backend);
        scores.put("API Design", api);
        scores.put("Error Handling", error);
        scores.put("Code Quality", quality);
        scores.put("Documentation", docs);

        return scores.entrySet().stream()
            .filter(e -> e.getValue() < 70)
            .sorted(Map.Entry.comparingByValue())
            .limit(3)
            .map(e -> e.getKey() + " (" + e.getValue() + "/100)")
            .collect(Collectors.toList());
    }

    private int computeAverageAnswerLength(List<Answer> answers, Set<Integer> skippedQuestionNumbers) {
        int totalChars = 0;
        int count = 0;
        for (Answer answer : answers) {
            int questionNumber = answer.getQuestion().getQuestionNumber();
            if (skippedQuestionNumbers.contains(questionNumber)) {
                continue;
            }
            String text = answer.getAnswerText() != null ? answer.getAnswerText().trim() : "";
            totalChars += text.length();
            count++;
        }
        return count == 0 ? 0 : totalChars / count;
    }

    private int computeScoreSpread(List<Answer> answers) {
        if (answers.isEmpty()) {
            return 0;
        }

        int minScore = Integer.MAX_VALUE;
        int maxScore = Integer.MIN_VALUE;
        for (Answer answer : answers) {
            Integer accuracyVal = answer.getAccuracyScore();
            Integer depthVal = answer.getDepthScore();
            Integer specificityVal = answer.getSpecificityScore();
            int accuracy = accuracyVal != null ? accuracyVal : 0;
            int depth = depthVal != null ? depthVal : 0;
            int specificity = specificityVal != null ? specificityVal : 0;
            int questionScore100 = ((accuracy * 4 + depth * 3 + specificity * 3) / 10) * 10;
            minScore = Math.min(minScore, questionScore100);
            maxScore = Math.max(maxScore, questionScore100);
        }

        return maxScore - minScore;
    }

    private void validateEvaluationCompleteness(Map<String, Object> evaluationResult,
                                                int expectedResultCount,
                                                Long sessionId) {
        Object resultsObj = evaluationResult.get("results");
        if (!(resultsObj instanceof List<?> results)) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_INCOMPLETE",
                "AI evaluation results were not returned in the expected format.",
                Map.of("sessionId", sessionId)
            );
        }

        if (results.size() != expectedResultCount) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_INCOMPLETE",
                "AI evaluation did not return scores for every answered question.",
                Map.of(
                    "sessionId", sessionId,
                    "expectedResults", expectedResultCount,
                    "actualResults", results.size()
                )
            );
        }
    }

    private int requireScore(Object value, int questionNumber, String fieldName) {
        if (!(value instanceof Number num)) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_INCOMPLETE",
                "Invalid AI score for question " + questionNumber + ".",
                Map.of("questionNumber", questionNumber, "field", fieldName)
            );
        }
        int score = num.intValue();
        if (score < 0 || score > 10) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_INCOMPLETE",
                "Out-of-range AI score for question " + questionNumber + ".",
                Map.of("questionNumber", questionNumber, "field", fieldName, "value", score)
            );
        }
        return score;
    }

    private double requireCompositeScore(Object value, int questionNumber) {
        if (!(value instanceof Number num)) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_INCOMPLETE",
                "Invalid AI composite score for question " + questionNumber + ".",
                Map.of("questionNumber", questionNumber, "field", "composite_score")
            );
        }
        return num.doubleValue();
    }

    private String extractFeedback(Map<?, ?> aiResult, int questionNumber) {
        Object feedbackObj = aiResult.get("ai_feedback");
        if (feedbackObj instanceof String feedbackText && !feedbackText.isBlank()) {
            return feedbackText;
        }
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AI_EVALUATION_INCOMPLETE",
            "Missing AI feedback for question " + questionNumber + ".",
            Map.of("questionNumber", questionNumber, "field", "ai_feedback")
        );
    }
}
