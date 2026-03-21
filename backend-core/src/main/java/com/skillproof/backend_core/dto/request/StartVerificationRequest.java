package com.skillproof.backend_core.dto.request;



import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartVerificationRequest {

    @NotBlank(message = "Repository owner is required")
    private String repoOwner;

    @NotBlank(message = "Repository name is required")
    private String repoName;
}
