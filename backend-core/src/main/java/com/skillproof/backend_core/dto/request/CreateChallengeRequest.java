package com.skillproof.backend_core.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateChallengeRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title must be at most 120 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 4000, message = "Description must be at most 4000 characters")
    private String description;

    @NotBlank(message = "Language is required")
    private String language;

    @Size(max = 50000, message = "Starter code must be at most 50,000 characters")
    private String starterCode;

    @Size(max = 50000, message = "Reference solution must be at most 50,000 characters")
    private String referenceSolution;

    @Valid
    @NotEmpty(message = "At least one test case is required")
    private List<TestCaseInput> testCases;

    @Min(value = 1, message = "Time limit must be at least 1 second")
    @Max(value = 120, message = "Time limit must be at most 120 seconds")
    private Integer timeLimitSeconds;

    private LocalDateTime expiresAt;

    @Data
    public static class TestCaseInput {
        @Size(max = 5000, message = "Input must be at most 5000 characters")
        private String stdin;

        @NotBlank(message = "Expected output is required")
        @Size(max = 5000, message = "Expected output must be at most 5000 characters")
        private String expectedOutput;
    }
}
