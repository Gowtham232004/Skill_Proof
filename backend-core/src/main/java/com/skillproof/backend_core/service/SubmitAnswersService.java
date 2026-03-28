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
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.SubmitAnswersRequest;
import com.skillproof.backend_core.dto.request.SubmitFollowUpAnswersRequest;
import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.dto.response.VerificationResultResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Answer;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.AnswerRepository;
import com.skillproof.backend_core.repository.BadgeRepository;
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
    private final BadgeRepository badgeRepository;
    private final ObjectMapper objectMapper;
    private final AiGatewayService aiGatewayService;
    private final BadgeService badgeService;
    private final GapAnalyzerService gapAnalyzerService;

    @Value("${phase2.weighted-scoring-enabled:false}")
    private boolean weightedScoringEnabled;

    @Value("${phase2.code-weight-percent:60}")
    private int codeWeightPercent;

    @Value("${phase2.conceptual-weight-percent:40}")
    private int conceptualWeightPercent;

    private static final String BADGE_BASE_URL = "http://localhost:3000/badge/";
    private static final int MIN_ANSWER_LENGTH = 20;
    private static final int MAX_FOLLOW_UP_QUESTIONS = 2;

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
        List<Integer> followUpRequired = computeFollowUpRequiredQuestionNumbers(
            savedAnswers,
            skippedQuestionNumbers
        );
        List<VerificationResultResponse.FollowUpQuestionDto> followUpQuestions =
            generateFollowUpQuestions(savedAnswers, followUpRequired);

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
        int rawOverallScore = savedAnswers.isEmpty() ? 0 : (total * 10) / savedAnswers.size();
        int backendScore     = backendRaw * 10;
        int apiScore         = apiRaw * 10;
        int errorScore       = errorRaw * 10;
        int qualityScore     = qualityRaw * 10;
        int documentScore    = docsRaw * 10;
        Map<String, Integer> scoreByQuestionType = computeScoreByQuestionType(savedAnswers);
        int technicalScore = resolveTechnicalScore(scoreByQuestionType, rawOverallScore);

        int skipCount = skippedQuestionNumbers.size();
        int avgAnswerLength = computeAverageAnswerLength(savedAnswers, skippedQuestionNumbers);
        int scoreSpread = computeScoreSpread(savedAnswers);
        boolean evaluationComplete = skippedQuestionNumbers.isEmpty();
        String confidenceTier = ConfidenceTierCalculator.computeTier(
            skipCount,
            avgAnswerLength,
            scoreSpread,
            evaluationComplete
        );

        Integer tabSwitches = Objects.requireNonNullElse(request.getTotalTabSwitches(), 0);
        Integer pasteCount = Objects.requireNonNullElse(request.getPasteCount(), 0);
        Integer totalCopyEvents = Objects.requireNonNullElse(request.getTotalCopyEvents(), 0);
        Integer avgAnswerSeconds = Objects.requireNonNullElse(request.getAvgAnswerSeconds(), 0);
        boolean coachingPatternDetected = hasHighAccuracyLowDepthPattern(savedAnswers, skippedQuestionNumbers);

        Map<String, Integer> integrityPenaltyBreakdown = computeIntegrityPenaltyBreakdown(
            tabSwitches,
            pasteCount,
            totalCopyEvents,
            avgAnswerSeconds,
            coachingPatternDetected
        );
        int integrityPenaltyTotal = computePenaltyTotal(integrityPenaltyBreakdown);
        int integrityAdjustedScore = Math.max(0, technicalScore - integrityPenaltyTotal);
        
        log.info("Skill scores computed: backend={}, api={}, error={}, quality={}, docs={}", 
            backendScore, apiScore, errorScore, qualityScore, documentScore);

        // 7. Create badge with properly distributed skill scores
        Badge badge = badgeService.createBadge(
            session,
            integrityAdjustedScore,
            technicalScore,
            integrityAdjustedScore,
            integrityPenaltyTotal,
            integrityPenaltyBreakdown,
            backendScore,
            apiScore,
            errorScore, qualityScore, documentScore,
            confidenceTier, tabSwitches, pasteCount, avgAnswerSeconds
        );

        try {
            List<Map<String, Object>> followUpPromptAudit = followUpQuestions.stream()
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("questionNumber", item.getQuestionNumber());
                    map.put("followUpQuestion", item.getFollowUpQuestion());
                    return map;
                })
                .toList();
            badge.setFollowUpPromptsJson(objectMapper.writeValueAsString(followUpPromptAudit));
            badgeRepository.save(badge);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize follow-up prompts for badge {}", badge.getId(), ex);
        }

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
            .technicalScore(technicalScore)
            .integrityAdjustedScore(integrityAdjustedScore)
            .integrityPenaltyTotal(integrityPenaltyTotal)
            .integrityPenaltyBreakdown(integrityPenaltyBreakdown)
            .scoreByQuestionType(scoreByQuestionType)
            .weightedScoringEnabled(weightedScoringEnabled)
            .codeWeightPercent(codeWeightPercent)
            .conceptualWeightPercent(conceptualWeightPercent)
            .backendScore(backendScore)
            .apiDesignScore(apiScore)
            .errorHandlingScore(errorScore)
            .codeQualityScore(qualityScore)
            .documentationScore(documentScore)
            .questionResults(questionResults)
            .followUpRequired(followUpRequired)
            .followUpRequiredCount(followUpRequired.size())
            .followUpAnsweredCount(0)
            .followUpQuestions(followUpQuestions)
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

    @Transactional
    public VerificationResultResponse submitFollowUpAnswers(Long userId,
                                                            SubmitFollowUpAnswersRequest request) {
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

        Badge badge = badgeRepository.findBySessionId(session.getId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "BADGE_NOT_FOUND",
                "Badge not found for session " + session.getId()
            ));

        List<Question> sessionQuestions = questionRepository.findBySessionOrderByQuestionNumber(session);
        Map<Integer, Question> questionsByNumber = sessionQuestions.stream()
            .collect(Collectors.toMap(Question::getQuestionNumber, q -> q));
        Map<Integer, String> issuedFollowUpByQuestion = readIssuedFollowUpPrompts(badge);

        Set<Integer> seenQuestionNumbers = new HashSet<>();

        List<Map<String, Object>> payload = new ArrayList<>();
        List<Map<String, Object>> followUpAudit = new ArrayList<>();
        int followUpRequiredCount = request.getFollowUps().size();
        int followUpAnsweredCount = 0;
        int payloadIndex = 1;

        for (SubmitFollowUpAnswersRequest.FollowUpAnswerItem item : request.getFollowUps()) {
            if (!seenQuestionNumbers.add(item.getQuestionNumber())) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FOLLOW_UP_DUPLICATE_QUESTION",
                    "Duplicate follow-up submission for question " + item.getQuestionNumber()
                );
            }

            Question sourceQuestion = questionsByNumber.get(item.getQuestionNumber());
            if (sourceQuestion == null) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FOLLOW_UP_QUESTION_NOT_FOUND",
                    "Original question not found for question number " + item.getQuestionNumber()
                );
            }

            if (!issuedFollowUpByQuestion.isEmpty()) {
                String issuedQuestion = issuedFollowUpByQuestion.get(item.getQuestionNumber());
                if (issuedQuestion == null || !normalizePrompt(issuedQuestion).equals(normalizePrompt(item.getFollowUpQuestion()))) {
                    throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "FOLLOW_UP_PROMPT_MISMATCH",
                        "Submitted follow-up prompt does not match issued prompt for question " + item.getQuestionNumber(),
                        Map.of("questionNumber", item.getQuestionNumber())
                    );
                }
            }

            boolean skipped = Boolean.TRUE.equals(item.getSkipped());
            String answerText = item.getAnswerText() != null ? item.getAnswerText().trim() : "";
            if (!skipped && answerText.length() < MIN_ANSWER_LENGTH) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ANSWER_TOO_SHORT",
                    "Follow-up answer too short for question " + item.getQuestionNumber() + ".",
                    Map.of(
                        "questionNumber", item.getQuestionNumber(),
                        "minChars", MIN_ANSWER_LENGTH,
                        "actualChars", answerText.length()
                    )
                );
            }

            Map<String, Object> auditEntry = new LinkedHashMap<>();
            auditEntry.put("questionNumber", item.getQuestionNumber());
            auditEntry.put("followUpQuestion", item.getFollowUpQuestion());
            auditEntry.put("skipped", skipped);
            auditEntry.put("answerLength", answerText.length());
            auditEntry.put("answerExcerpt", toAnswerExcerpt(answerText));
            followUpAudit.add(auditEntry);

            if (skipped) {
                continue;
            }

            followUpAnsweredCount++;
            Map<String, Object> entry = new HashMap<>();
            entry.put("question_id", payloadIndex++);
            entry.put("question_text", item.getFollowUpQuestion());
            entry.put("file_reference", sourceQuestion.getFileReference());
            entry.put("code_context", sourceQuestion.getCodeContext() != null ? sourceQuestion.getCodeContext() : "");
            entry.put("answer_text", answerText);
            payload.add(entry);
        }

        int followUpScore = 0;
        if (!payload.isEmpty()) {
            Map<String, Object> evaluation = aiGatewayService.evaluateAnswers(
                session.getId(),
                payload,
                session.getRepoLanguage()
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) evaluation.getOrDefault("results", List.of());
            if (results.size() != payload.size()) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_EVALUATION_INCOMPLETE",
                    "Follow-up evaluation did not return complete results."
                );
            }

            int total = 0;
            for (Map<String, Object> result : results) {
                int accuracy = requireScore(result.get("accuracy_score"), 0, "accuracy_score");
                int depth = requireScore(result.get("depth_score"), 0, "depth_score");
                int specificity = requireScore(result.get("specificity_score"), 0, "specificity_score");
                int normalized = ((accuracy * 4 + depth * 3 + specificity * 3) / 10) * 10;
                total += normalized;
            }
            followUpScore = (int) Math.round(total / (double) results.size());
        }

        int baseTechnical = badge.getTechnicalScore() != null
            ? badge.getTechnicalScore()
            : Objects.requireNonNullElse(badge.getOverallScore(), 0);
        int blendedTechnical = (int) Math.round(baseTechnical * 0.8 + followUpScore * 0.2);
        int penaltyTotal = Objects.requireNonNullElse(badge.getIntegrityPenaltyTotal(), 0);
        int adjusted = Math.max(0, blendedTechnical - penaltyTotal);
        Map<String, Integer> integrityPenaltyBreakdown = new LinkedHashMap<>();
        if (badge.getIntegrityPenaltyBreakdown() != null
            && !badge.getIntegrityPenaltyBreakdown().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Number> raw = objectMapper.readValue(
                    badge.getIntegrityPenaltyBreakdown(),
                    Map.class
                );
                for (Map.Entry<String, Number> entry : raw.entrySet()) {
                    integrityPenaltyBreakdown.put(entry.getKey(), entry.getValue().intValue());
                }
            } catch (JsonProcessingException ex) {
                log.warn("Failed to parse integrity penalty breakdown for badge {}", badge.getId(), ex);
            }
        }

        List<Answer> persistedAnswers = answerRepository
            .findByQuestionSessionIdOrderByQuestionQuestionNumberAsc(session.getId());
        Map<String, Integer> scoreByQuestionType = computeScoreByQuestionType(persistedAnswers);
        List<BadgeResponse.QuestionResultDto> questionResults =
            buildQuestionResultsFromStoredAnswers(persistedAnswers);

        int backendScore = Objects.requireNonNullElse(badge.getBackendScore(), 0);
        int apiScore = Objects.requireNonNullElse(badge.getApiDesignScore(), 0);
        int errorScore = Objects.requireNonNullElse(badge.getErrorHandlingScore(), 0);
        int qualityScore = Objects.requireNonNullElse(badge.getCodeQualityScore(), 0);
        int docsScore = Objects.requireNonNullElse(badge.getDocumentationScore(), 0);

        badge.setTechnicalScore(blendedTechnical);
        badge.setIntegrityAdjustedScore(adjusted);
        badge.setOverallScore(adjusted);
        badge.setFollowUpRequiredCount(followUpRequiredCount);
        badge.setFollowUpAnsweredCount(followUpAnsweredCount);
        try {
            badge.setFollowUpResultsJson(objectMapper.writeValueAsString(followUpAudit));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize follow-up audit for badge {}", badge.getId(), ex);
            badge.setFollowUpResultsJson("[]");
        }
        if (followUpAnsweredCount == 0) {
            badge.setConfidenceTier("Low");
        }
        badgeRepository.save(badge);

        return VerificationResultResponse.builder()
            .sessionId(session.getId())
            .overallScore(adjusted)
            .technicalScore(blendedTechnical)
            .integrityAdjustedScore(adjusted)
            .integrityPenaltyTotal(penaltyTotal)
            .integrityPenaltyBreakdown(integrityPenaltyBreakdown)
            .scoreByQuestionType(scoreByQuestionType)
            .weightedScoringEnabled(weightedScoringEnabled)
            .codeWeightPercent(codeWeightPercent)
            .conceptualWeightPercent(conceptualWeightPercent)
            .backendScore(backendScore)
            .apiDesignScore(apiScore)
            .errorHandlingScore(errorScore)
            .codeQualityScore(qualityScore)
            .documentationScore(docsScore)
            .questionResults(questionResults)
            .followUpRequiredCount(followUpRequiredCount)
            .followUpAnsweredCount(followUpAnsweredCount)
            .badgeToken(badge.getVerificationToken())
            .badgeUrl(BADGE_BASE_URL + badge.getVerificationToken())
            .topGaps(identifyTopGaps(backendScore, apiScore, errorScore, qualityScore, docsScore))
            .repoAttemptCount(
                Math.toIntExact(sessionRepository.countByUserAndRepoOwnerAndRepoNameAndStatus(
                    session.getUser(),
                    session.getRepoOwner(),
                    session.getRepoName(),
                    VerificationSession.Status.COMPLETED
                ))
            )
            .confidenceTier(badge.getConfidenceTier())
            .tabSwitches(badge.getTabSwitches())
            .pasteCount(badge.getPasteCount())
            .avgAnswerSeconds(badge.getAvgAnswerSeconds())
            .status("COMPLETED")
            .build();
    }

    private String toAnswerExcerpt(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return "";
        }
        String trimmed = answerText.trim();
        if (trimmed.length() <= 280) {
            return trimmed;
        }
        return trimmed.substring(0, 280) + "...";
    }

    private Map<Integer, String> readIssuedFollowUpPrompts(Badge badge) {
        if (badge.getFollowUpPromptsJson() == null || badge.getFollowUpPromptsJson().isBlank()) {
            return Map.of();
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raw = objectMapper.readValue(
                badge.getFollowUpPromptsJson(),
                List.class
            );

            Map<Integer, String> prompts = new LinkedHashMap<>();
            for (Map<String, Object> item : raw) {
                Object qn = item.get("questionNumber");
                if (!(qn instanceof Number number)) {
                    continue;
                }
                prompts.put(number.intValue(), String.valueOf(item.getOrDefault("followUpQuestion", "")));
            }
            return prompts;
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse follow-up prompts for badge {}", badge.getId(), ex);
            return Map.of();
        }
    }

    private String normalizePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.trim().replaceAll("\\s+", " ");
    }

    private Map<String, Integer> computeIntegrityPenaltyBreakdown(int tabSwitches,
                                                                  int pasteCount,
                                                                  int totalCopyEvents,
                                                                  int avgAnswerSeconds,
                                                                  boolean coachingPatternDetected) {
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

        int copyPenalty = 0;
        if (totalCopyEvents >= 3) {
            copyPenalty = 3;
        } else if (totalCopyEvents > 0) {
            copyPenalty = 1;
        }

        int coachingPatternPenalty = coachingPatternDetected ? 2 : 0;

        int total = pastePenalty + speedPenalty + tabPenalty + copyPenalty + coachingPatternPenalty;
        if (total > 10) {
            int overflow = total - 10;
            if (tabPenalty >= overflow) {
                tabPenalty -= overflow;
            } else if (speedPenalty >= overflow - tabPenalty) {
                speedPenalty -= (overflow - tabPenalty);
                tabPenalty = 0;
            } else if (copyPenalty >= overflow - tabPenalty - speedPenalty) {
                copyPenalty -= (overflow - tabPenalty - speedPenalty);
                tabPenalty = 0;
                speedPenalty = 0;
            } else if (coachingPatternPenalty >= overflow - tabPenalty - speedPenalty - copyPenalty) {
                coachingPatternPenalty -= (overflow - tabPenalty - speedPenalty - copyPenalty);
                tabPenalty = 0;
                speedPenalty = 0;
                copyPenalty = 0;
            } else {
                pastePenalty = Math.max(0, pastePenalty - (overflow - tabPenalty - speedPenalty - copyPenalty - coachingPatternPenalty));
                speedPenalty = 0;
                tabPenalty = 0;
                copyPenalty = 0;
                coachingPatternPenalty = 0;
            }
        }

        breakdown.put("pastePenalty", pastePenalty);
        breakdown.put("speedPenalty", speedPenalty);
        breakdown.put("tabSwitchPenalty", tabPenalty);
        breakdown.put("copyPenalty", copyPenalty);
        breakdown.put("coachingPatternPenalty", coachingPatternPenalty);
        breakdown.put("copyEvents", Math.max(0, totalCopyEvents));
        breakdown.put("coachingPatternDetected", coachingPatternDetected ? 1 : 0);
        return breakdown;
    }

    private int computePenaltyTotal(Map<String, Integer> integrityPenaltyBreakdown) {
        return integrityPenaltyBreakdown.entrySet().stream()
            .filter(entry -> entry.getKey().endsWith("Penalty"))
            .map(Map.Entry::getValue)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
    }

    private boolean hasHighAccuracyLowDepthPattern(List<Answer> answers,
                                                   Set<Integer> skippedQuestionNumbers) {
        int suspiciousCount = 0;
        for (Answer answer : answers) {
            if (answer.getQuestion() == null) {
                continue;
            }
            if (skippedQuestionNumbers.contains(answer.getQuestion().getQuestionNumber())) {
                continue;
            }

            int accuracy = Objects.requireNonNullElse(answer.getAccuracyScore(), 0);
            int depth = Objects.requireNonNullElse(answer.getDepthScore(), 0);
            if (accuracy >= 8 && depth <= 3) {
                suspiciousCount++;
            }
        }

        return suspiciousCount >= 2;
    }

    private Map<String, Integer> computeScoreByQuestionType(List<Answer> answers) {
        Map<String, List<Integer>> groupedScores = new LinkedHashMap<>();

        for (Answer answer : answers) {
            Question question = answer.getQuestion();
            String questionType = question.getQuestionType() != null
                ? question.getQuestionType().name()
                : Question.QuestionType.CODE_GROUNDED.name();

            Integer accuracyVal = answer.getAccuracyScore();
            Integer depthVal = answer.getDepthScore();
            Integer specificityVal = answer.getSpecificityScore();
            int accuracy = accuracyVal != null ? accuracyVal : 0;
            int depth = depthVal != null ? depthVal : 0;
            int specificity = specificityVal != null ? specificityVal : 0;
            int questionScore = (accuracy * 4 + depth * 3 + specificity * 3) / 10;
            int normalized = questionScore * 10;

            groupedScores.computeIfAbsent(questionType, ignored -> new ArrayList<>())
                .add(normalized);
        }

        Map<String, Integer> scoreByQuestionType = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : groupedScores.entrySet()) {
            List<Integer> values = entry.getValue();
            int avg = values.isEmpty()
                ? 0
                : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
            scoreByQuestionType.put(entry.getKey(), avg);
        }

        return scoreByQuestionType;
    }

    private int resolveTechnicalScore(Map<String, Integer> scoreByQuestionType,
                                      int fallbackScore) {
        if (!weightedScoringEnabled) {
            return fallbackScore;
        }

        Integer codeScore = scoreByQuestionType.get("CODE_GROUNDED");
        Integer conceptualScore = scoreByQuestionType.get("CONCEPTUAL");
        if (codeScore == null || conceptualScore == null) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "WEIGHTED_SCORE_INPUT_INCOMPLETE",
                "Weighted scoring requires both code-grounded and conceptual scores.",
                Map.of("scoreByQuestionType", scoreByQuestionType)
            );
        }

        int safeCodeWeight = Math.max(0, codeWeightPercent);
        int safeConceptualWeight = Math.max(0, conceptualWeightPercent);
        int totalWeight = safeCodeWeight + safeConceptualWeight;
        if (totalWeight == 0) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "WEIGHTED_SCORE_CONFIG_INVALID",
                "Weighted scoring weights must sum to a positive value.",
                Map.of(
                    "codeWeightPercent", codeWeightPercent,
                    "conceptualWeightPercent", conceptualWeightPercent
                )
            );
        }

        double weighted = (codeScore * safeCodeWeight + conceptualScore * safeConceptualWeight)
            / (double) totalWeight;
        return (int) Math.round(weighted);
    }

    private List<Integer> computeFollowUpRequiredQuestionNumbers(List<Answer> answers,
                                                                 Set<Integer> skippedQuestionNumbers) {
        List<Map.Entry<Integer, Integer>> candidates = new ArrayList<>();

        for (Answer answer : answers) {
            Question question = answer.getQuestion();
            int questionNumber = question.getQuestionNumber();

            if (skippedQuestionNumbers.contains(questionNumber)) {
                continue;
            }

            Question.QuestionType questionType = question.getQuestionType() != null
                ? question.getQuestionType()
                : Question.QuestionType.CODE_GROUNDED;
            if (questionType != Question.QuestionType.CODE_GROUNDED) {
                continue;
            }

            Integer specificityScore = answer.getSpecificityScore();
            int specificity = 0;
            if (specificityScore != null) {
                specificity = specificityScore;
            }
            if (specificity < 5) {
                candidates.add(Map.entry(questionNumber, specificity));
            }
        }

        candidates.sort(Map.Entry.comparingByValue());
        List<Integer> followUps = new ArrayList<>();
        for (Map.Entry<Integer, Integer> candidate : candidates) {
            if (followUps.size() >= MAX_FOLLOW_UP_QUESTIONS) {
                break;
            }
            followUps.add(candidate.getKey());
        }

        return followUps;
    }

    private List<VerificationResultResponse.FollowUpQuestionDto> generateFollowUpQuestions(
            List<Answer> answers,
            List<Integer> followUpRequired) {

        if (followUpRequired == null || followUpRequired.isEmpty()) {
            return List.of();
        }

        Map<Integer, Answer> answersByQuestionNumber = answers.stream()
            .collect(Collectors.toMap(a -> a.getQuestion().getQuestionNumber(), a -> a));

        List<VerificationResultResponse.FollowUpQuestionDto> followUps = new ArrayList<>();
        for (Integer questionNumber : followUpRequired) {
            Answer answer = answersByQuestionNumber.get(questionNumber);
            if (answer == null) {
                continue;
            }

            Question question = answer.getQuestion();
            Map<String, String> generated;
            try {
                generated = aiGatewayService.generateFollowUp(
                    question.getQuestionText(),
                    question.getFileReference(),
                    question.getCodeContext(),
                    answer.getAnswerText()
                );
            } catch (ApiException ex) {
                log.warn(
                    "Follow-up AI unavailable for session {} question {}. Using fallback follow-up.",
                    question.getSession().getId(),
                    questionNumber,
                    ex
                );
                generated = Map.of(
                    "followupQuestion",
                    "In " + (question.getFileReference() != null ? question.getFileReference() : "this file")
                        + ", explain one concrete runtime risk in your implementation and describe the exact code change you would make to fix it.",
                    "targetsIdentifier",
                    question.getFileReference() != null ? question.getFileReference() : ""
                );
            }

            followUps.add(VerificationResultResponse.FollowUpQuestionDto.builder()
                .questionNumber(questionNumber)
                .fileReference(question.getFileReference())
                .originalQuestion(question.getQuestionText())
                .followUpQuestion(generated.getOrDefault("followupQuestion", ""))
                .targetsIdentifier(generated.getOrDefault("targetsIdentifier", ""))
                .build());
        }

        return followUps;
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
                    .questionType(
                        q.getQuestionType() != null
                            ? q.getQuestionType().name()
                            : Question.QuestionType.CODE_GROUNDED.name()
                    )
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
                .questionType(
                    q.getQuestionType() != null
                        ? q.getQuestionType().name()
                        : Question.QuestionType.CODE_GROUNDED.name()
                )
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

    private List<BadgeResponse.QuestionResultDto> buildQuestionResultsFromStoredAnswers(List<Answer> answers) {
        return answers.stream()
            .map(answer -> {
                Question q = answer.getQuestion();
                return BadgeResponse.QuestionResultDto.builder()
                    .questionNumber(q.getQuestionNumber())
                    .difficulty(q.getDifficulty().name())
                    .questionType(
                        q.getQuestionType() != null
                            ? q.getQuestionType().name()
                            : Question.QuestionType.CODE_GROUNDED.name()
                    )
                    .fileReference(q.getFileReference())
                    .questionText(q.getQuestionText())
                    .accuracyScore(Objects.requireNonNullElse(answer.getAccuracyScore(), 0))
                    .depthScore(Objects.requireNonNullElse(answer.getDepthScore(), 0))
                    .specificityScore(Objects.requireNonNullElse(answer.getSpecificityScore(), 0))
                    .compositeScore(Objects.requireNonNullElse(answer.getCompositeScore(), 0.0))
                    .aiFeedback(Objects.requireNonNullElse(answer.getAiFeedback(), "No feedback available."))
                    .build();
            })
            .sorted(Comparator.comparing(BadgeResponse.QuestionResultDto::getQuestionNumber))
            .toList();
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
