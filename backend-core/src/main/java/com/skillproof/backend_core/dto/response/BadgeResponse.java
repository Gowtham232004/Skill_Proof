package com.skillproof.backend_core.dto.response;



import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BadgeResponse {

    // Public badge info — shown on skillproof.dev/badge/{token}
    private boolean valid;
    private String verificationToken;
    private String badgeUrl;

    // Developer info
    private String githubUsername;
    private String avatarUrl;
    private String displayName;

    // Repo info
    private String repoName;
    private String repoOwner;
    private String repoDescription;
    private String primaryLanguage;
    private List<String> frameworksDetected;

    // Scores
    private Integer overallScore;
    private Integer technicalScore;
    private Integer integrityAdjustedScore;
    private Integer executionAdjustedScore;
    private Integer integrityPenaltyTotal;
    private Map<String, Integer> integrityPenaltyBreakdown;
    private ExecutionBackedSignalDto executionBackedSignal;
    private AssistLikelihoodDto assistLikelihood;
    private Map<String, Integer> scoreByQuestionType;
    private Boolean weightedScoringEnabled;
    private Integer codeWeightPercent;
    private Integer conceptualWeightPercent;
    private Integer backendScore;
    private Integer apiDesignScore;
    private Integer errorHandlingScore;
    private Integer codeQualityScore;
    private Integer documentationScore;

    private String confidenceTier;

    private Integer tabSwitches;
    private Integer pasteCount;
    private Integer avgAnswerSeconds;
    private Integer followUpRequiredCount;
    private Integer followUpAnsweredCount;
    private List<FollowUpResultDto> followUpResults;
    private Integer answeredCount;
    private Integer totalQuestions;
    private Integer skippedCount;
    private Boolean evaluationComplete;
    private String confidenceExplanation;
    private Boolean answerRevealAvailable;
    private String recruiterDecisionStatus;
    private String recruiterDecisionReason;
    private String recruiterDecisionNotes;
    private String recruiterDecisionBy;
    private LocalDateTime recruiterDecisionAt;

    // Question results — shown as score breakdown
    private List<QuestionResultDto> questionResults;

    // Number of completed verifications for this repo by this user
    private Integer repoAttemptCount;

    private LocalDateTime issuedAt;

    @Data
    @Builder
    public static class QuestionResultDto {
        private Integer questionNumber;
        private String difficulty;
        private String questionType;
        private String fileReference;
        private String questionText;
        private String questionCodeSnippet;
        private Boolean skipped;
        private Integer answerLength;
        private String maskedAnswerExcerpt;
        private String fullAnswerText;
        private Integer accuracyScore;
        private Integer depthScore;
        private Integer specificityScore;
        private Double compositeScore;
        private String aiFeedback;
        private List<String> keyPoints;
    }

    @Data
    @Builder
    public static class FollowUpResultDto {
        private Integer questionNumber;
        private String followUpQuestion;
        private Boolean skipped;
        private Integer answerLength;
        private String answerExcerpt;
    }

    @Data
    @Builder
    public static class ExecutionBackedSignalDto {
        private Integer totalAttempts;
        private Integer passedCount;
        private Integer failedCount;
        private Integer errorCount;
        private Integer timeoutCount;
        private Integer passRatePercent;
        private Integer avgExecutionScore;
        private String latestStatus;
        private Integer executionPenalty;
        private Integer executionAdjustedScore;
        private String signalLevel;
    }

    @Data
    @Builder
    public static class AssistLikelihoodDto {
        private String level;
        private Integer score;
        private List<String> reasons;
        private Boolean advisoryOnly;
    }
}