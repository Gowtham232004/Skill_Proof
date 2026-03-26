package com.skillproof.backend_core.dto.response;



import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerificationResultResponse {

    private Long sessionId;
    private Integer overallScore;
    private Integer technicalScore;
    private Integer integrityAdjustedScore;
    private Integer integrityPenaltyTotal;
    private Map<String, Integer> integrityPenaltyBreakdown;
    private Map<String, Integer> scoreByQuestionType;
    private Boolean weightedScoringEnabled;
    private Integer codeWeightPercent;
    private Integer conceptualWeightPercent;

    // Skill dimension scores 0-100
    private Integer backendScore;
    private Integer apiDesignScore;
    private Integer errorHandlingScore;
    private Integer codeQualityScore;
    private Integer documentationScore;

    // Individual question results
    private List<BadgeResponse.QuestionResultDto> questionResults;

    // Questions that require follow-up due to weak code-grounded specificity
    private List<Integer> followUpRequired;
    private Integer followUpRequiredCount;
    private Integer followUpAnsweredCount;

    // Generated follow-up questions for additional verification step
    private List<FollowUpQuestionDto> followUpQuestions;

    // Badge info
    private String badgeToken;
    private String badgeUrl;

    // Top 3 skill gaps identified
    private List<String> topGaps;

    // Number of completed verifications for this repo by this user
    private Integer repoAttemptCount;

    private String confidenceTier;
    private Integer tabSwitches;
    private Integer pasteCount;
    private Integer avgAnswerSeconds;

    private String status;

    @Data
    @Builder
    public static class FollowUpQuestionDto {
        private Integer questionNumber;
        private String fileReference;
        private String originalQuestion;
        private String followUpQuestion;
        private String targetsIdentifier;
    }
}