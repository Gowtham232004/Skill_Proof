package com.skillproof.backend_core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRecruiterDecisionRequest {

    @NotBlank(message = "Decision status is required")
    @Size(max = 32, message = "Decision status must be <= 32 characters")
    private String status;

    @NotBlank(message = "Decision reason is required")
    @Size(max = 255, message = "Decision reason must be <= 255 characters")
    private String reason;

    @Size(max = 4000, message = "Decision notes must be <= 4000 characters")
    private String notes;
}
