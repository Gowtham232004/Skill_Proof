package com.skillproof.backend_core.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveSessionResponse {
    private Long id;
    private String sessionCode;
    private String badgeToken;
    private String candidateUsername;
    private String repoName;
    private Integer currentRevealedQuestion;
    private String status;
    private Integer liveScore;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String recruiterUrl;
    private String candidateUrl;
    private Integer totalQuestions;
}
