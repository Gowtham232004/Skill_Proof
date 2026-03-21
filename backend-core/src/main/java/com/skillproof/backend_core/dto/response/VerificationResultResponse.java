package com.skillproof.backend_core.dto.response;



import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerificationResultResponse {

    private Long sessionId;
    private Integer overallScore;

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

    private String status;
}