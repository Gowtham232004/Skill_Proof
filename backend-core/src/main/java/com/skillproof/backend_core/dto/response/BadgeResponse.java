package com.skillproof.backend_core.dto.response;



import java.time.LocalDateTime;
import java.util.List;

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
    private Integer backendScore;
    private Integer apiDesignScore;
    private Integer errorHandlingScore;
    private Integer codeQualityScore;
    private Integer documentationScore;

    // Question results — shown as score breakdown
    private List<QuestionResultDto> questionResults;

    private LocalDateTime issuedAt;

    @Data
    @Builder
    public static class QuestionResultDto {
        private Integer questionNumber;
        private String difficulty;
        private String fileReference;
        private String questionText;
        private Integer accuracyScore;
        private Integer depthScore;
        private Integer specificityScore;
        private Double compositeScore;
        private String aiFeedback;
    }
}