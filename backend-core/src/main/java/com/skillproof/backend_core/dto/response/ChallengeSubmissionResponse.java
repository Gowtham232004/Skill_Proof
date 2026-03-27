package com.skillproof.backend_core.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.skillproof.backend_core.model.ChallengeSubmission;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChallengeSubmissionResponse {

    private Long submissionId;
    private Long challengeId;
    private String candidateUsername;
    private Integer score;
    private ChallengeSubmission.SubmissionStatus status;
    private String feedback;
    private String stdout;
    private String stderr;
    private String submittedCode;
    private List<TestCaseResultDto> testCases;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class TestCaseResultDto {
        private Integer caseNumber;
        private String name;
        private String status;
        private String expectedOutput;
        private String actualOutput;
        private String errorMessage;
    }
}
