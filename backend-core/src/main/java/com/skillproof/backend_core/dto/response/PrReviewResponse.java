package com.skillproof.backend_core.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PrReviewResponse {

    private Long id;
    private String reviewToken;
    private String badgeToken;
    private String candidateUsername;
    private String repoName;
    private String filePath;
    private String modifiedCode;
    private String status;
    private Integer overallScore;
    private Integer bugsFoundCount;
    private String aiFeedback;
    private Integer timeTakenSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private String candidateUrl;

    private String bugDescription;
    private String originalCode;
    private List<ReviewComment> comments;

    @Data
    @Builder
    public static class ReviewComment {
        private Integer lineNumber;
        private String comment;
        private String severity;
    }
}
