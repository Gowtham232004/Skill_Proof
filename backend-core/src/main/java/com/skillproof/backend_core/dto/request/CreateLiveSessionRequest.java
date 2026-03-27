package com.skillproof.backend_core.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateLiveSessionRequest {

    @NotBlank(message = "Badge token is required")
    private String badgeToken;

    private String candidateEmail;
}
