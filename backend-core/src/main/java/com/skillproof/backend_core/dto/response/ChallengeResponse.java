package com.skillproof.backend_core.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChallengeResponse {

    private Long id;
    private String title;
    private String description;
    private String language;
    private String challengeMode;
    private String accessMode;
    private List<String> assignedCandidateUsernames;
    private String starterCode;
    private Integer timeLimitSeconds;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private String recruiterUsername;
    private String sourceBadgeToken;
    private String sourceRepoName;
    private String sourceFilePath;
    private String sourceSnippetHash;
    private String generationReason;
    private Integer totalTestCases;
    private Integer visibleTestCases;
    private List<TestCasePreview> testCases;

    @Data
    @Builder
    public static class TestCasePreview {
        private Integer caseNumber;
        private String name;
        private String stdin;
        private Boolean isVisible;
        private String expectedOutput;
    }
}
