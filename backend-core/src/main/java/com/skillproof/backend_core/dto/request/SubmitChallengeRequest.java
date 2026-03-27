package com.skillproof.backend_core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitChallengeRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 100000, message = "Code must be at most 100,000 characters")
    private String code;
}
