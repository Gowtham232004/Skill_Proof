package com.skillproof.backend_core.service;



import com.skillproof.backend_core.dto.request.SubmitAnswersRequest;
import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.dto.response.VerificationResultResponse;
import com.skillproof.backend_core.model.*;
import com.skillproof.backend_core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmitAnswersService {

    private final VerificationSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final AiGatewayService aiGatewayService;
    private final BadgeService badgeService;

    private static final String BADGE_BASE_URL = "http://localhost:3000/badge/";

    @Transactional
    public VerificationResultResponse submitAnswers(Long userId,
                                                     SubmitAnswersRequest request) {

        // 1. Load and validate session
        VerificationSession session = sessionRepository.findById(request.getSessionId())
            .orElseThrow(() -> new RuntimeException(
                "Session not found: " + request.getSessionId()));

        if (!session.getUser().getId().equals(userId)) {
            throw new RuntimeException("Session does not belong to this user");
        }

        if (session.getStatus() == VerificationSession.Status.COMPLETED) {
            throw new RuntimeException("Session already completed");
        }

        log.info("Processing answer submission for session {} user {}",
            request.getSessionId(), userId);

        // 2. Load questions for this session
        List<Question> questions = questionRepository
            .findBySessionOrderByQuestionNumber(session);

        if (questions.isEmpty()) {
            throw new RuntimeException("No questions found for session " +
                request.getSessionId());
        }

        // 3. Save answers to database
        Map<Long, Question> questionMap = questions.stream()
            .collect(Collectors.toMap(Question::getId, q -> q));

        List<Answer> savedAnswers = new ArrayList<>();
        for (SubmitAnswersRequest.AnswerItem item : request.getAnswers()) {
            Question question = questionMap.get(item.getQuestionId());
            if (question == null) {
                log.warn("Question {} not found for session {}, skipping",
                    item.getQuestionId(), request.getSessionId());
                continue;
            }
            Answer answer = Answer.builder()
                .question(question)
                .answerText(item.getAnswerText())
                .build();
            savedAnswers.add(answerRepository.save(answer));
        }

        log.info("Saved {} answers for session {}", savedAnswers.size(),
            request.getSessionId());

        // 4. Call AI service to evaluate answers
        List<Map<String, Object>> answersForAi = buildAiEvaluationPayload(
            savedAnswers, questionMap);

        Map<String, Object> evaluationResult = aiGatewayService.evaluateAnswers(
            session.getId(), answersForAi, session.getRepoLanguage()
        );

        // 5. Update saved answers with AI scores and extract evaluation results
        List<BadgeResponse.QuestionResultDto> questionResults =
            updateAnswersWithScores(savedAnswers, evaluationResult, questions);

        // 6. Compute individual skill scores from answer rubric scores
        // Map: Q1→backend, Q2→api, Q3→error, Q4→quality, Q5→documentation
        int total = 0;
        int backendRaw = 0, apiRaw = 0, errorRaw = 0, qualityRaw = 0, docsRaw = 0;

        List<?> aiResults = (List<?>) evaluationResult.getOrDefault("results", List.of());
        log.debug("AI evaluation response keys: {}", evaluationResult.keySet());
        log.debug("AI results list size: {}", aiResults.size());
        
        for (int i = 0; i < Math.min(aiResults.size(), savedAnswers.size()); i++) {
            Object resultObj = aiResults.get(i);
            if (!(resultObj instanceof Map)) {
                log.debug("Result {} is not a Map, skipping", i);
                continue;
            }
            
            Map<?, ?> resultMap = (Map<?, ?>) resultObj;
            log.debug("Result {} keys: {}", i, resultMap.keySet());
            
            int accuracy    = toInt(resultMap.get("accuracy_score"), 5);
            int depth       = toInt(resultMap.get("depth_score"), 5);
            int specificity = toInt(resultMap.get("specificity_score"), 5);
            
            log.debug("Q{}: accuracy={}, depth={}, specificity={}", i+1, accuracy, depth, specificity);
            
            // Weighted rubric: accuracy 40%, depth 30%, specificity 30% (scale 1-10)
            int questionScore = (accuracy * 4 + depth * 3 + specificity * 3) / 10;
            total += questionScore;
            
            // Distribute questions to skill dimensions by index
            switch (i) {
                case 0 -> backendRaw = questionScore;       // Q1 → backend logic
                case 1 -> apiRaw = questionScore;           // Q2 → api design
                case 2 -> errorRaw = questionScore;         // Q3 → error handling
                case 3 -> qualityRaw = questionScore;       // Q4 → code quality
                case 4 -> docsRaw = questionScore;          // Q5 → documentation
            }
        }

        // Convert from 1-10 scale to 0-100 scale
        int overallScore     = aiResults.isEmpty() ? 50 : (total * 10) / aiResults.size();
        int backendScore     = backendRaw * 10;
        int apiScore         = apiRaw * 10;
        int errorScore       = errorRaw * 10;
        int qualityScore     = qualityRaw * 10;
        int documentScore    = docsRaw * 10;
        
        log.info("Skill scores computed: backend={}, api={}, error={}, quality={}, docs={}", 
            backendScore, apiScore, errorScore, qualityScore, documentScore);

        // 7. Create badge with properly distributed skill scores
        Badge badge = badgeService.createBadge(
            session, overallScore, backendScore, apiScore,
            errorScore, qualityScore, documentScore
        );

        // 8. Mark session as completed
        session.setStatus(VerificationSession.Status.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        log.info("Verification complete. Session={} Score={} Badge={}",
            session.getId(), overallScore, badge.getVerificationToken());

        // 9. Build response
        return VerificationResultResponse.builder()
            .sessionId(session.getId())
            .overallScore(overallScore)
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
            .status("COMPLETED")
            .build();
    }

    // Build payload for AI evaluation
    private List<Map<String, Object>> buildAiEvaluationPayload(
            List<Answer> answers, Map<Long, Question> questionMap) {

        List<Map<String, Object>> payload = new ArrayList<>();
        for (Answer answer : answers) {
            Question question = answer.getQuestion();
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
    @SuppressWarnings("unchecked")
    private List<BadgeResponse.QuestionResultDto> updateAnswersWithScores(
            List<Answer> answers, Map<String, Object> evaluationResult,
            List<Question> questions) {

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
            Map<?, ?> aiResult = resultsByQuestionId.get(answer.getId());
            int accuracy = 5, depth = 5, specificity = 5;
            double composite = 5.0;
            String feedback = "Evaluation completed.";

            if (aiResult != null) {
                accuracy    = toInt(aiResult.get("accuracy_score"), 5);
                depth       = toInt(aiResult.get("depth_score"), 5);
                specificity = toInt(aiResult.get("specificity_score"), 5);
                composite   = toDouble(aiResult.get("composite_score"), 5.0);
                Object feedbackObj = aiResult.get("ai_feedback");
                if (feedbackObj instanceof String) {
                     feedback = (String) feedbackObj;
}
            }

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

    // Safe score extraction helpers
    @SuppressWarnings("unchecked")
    private int extractScore(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        return toInt(val, defaultVal);
    }

    @SuppressWarnings("unchecked")
    private int extractNestedScore(Map<String, Object> map, String nestedKey,
                                    String key, int defaultVal) {
        Object nested = map.get(nestedKey);
        if (nested instanceof Map) {
            return toInt(((Map<?, ?>) nested).get(key), defaultVal);
        }
        return defaultVal;
    }

    private int toInt(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        try { return ((Number) val).intValue(); }
        catch (Exception e) { return defaultVal; }
    }

    private double toDouble(Object val, double defaultVal) {
        if (val == null) return defaultVal;
        try { return ((Number) val).doubleValue(); }
        catch (Exception e) { return defaultVal; }
    }
}
