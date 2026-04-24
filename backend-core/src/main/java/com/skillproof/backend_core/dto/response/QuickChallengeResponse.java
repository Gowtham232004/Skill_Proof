package com.skillproof.backend_core.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuickChallengeResponse {
    private Long id;
    private String challengeToken;
    private String badgeToken;
    private String candidateUsername;
    private String repoName;
    private String selectedFilePath;
    private String status;
    private Integer overallScore;
    private Integer accuracyScore;
    private Integer depthScore;
    private Integer specificityScore;
    private String aiFeedback;
    private Integer tabSwitchCount;
    private Integer timeTakenSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime openedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private String codeSnippet;
    private String questionText;
    private Long secondsRemaining;
    private String candidateUrl;
    private String recruiterNotes;
    private String candidateAnswer;
}
