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
    private String starterCode;
    private Integer timeLimitSeconds;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private String recruiterUsername;
    private List<TestCasePreview> testCases;

    @Data
    @Builder
    public static class TestCasePreview {
        private String stdin;
        private String expectedOutput;
    }
}
