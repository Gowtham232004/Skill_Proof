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

    // Skill dimension scores 0-100
    private Integer backendScore;
    private Integer apiDesignScore;
    private Integer errorHandlingScore;
    private Integer codeQualityScore;
    private Integer documentationScore;

    // Individual question results
    private List<BadgeResponse.QuestionResultDto> questionResults;

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
}