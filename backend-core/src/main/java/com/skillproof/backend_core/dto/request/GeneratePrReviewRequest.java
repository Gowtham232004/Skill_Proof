package com.skillproof.backend_core.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GeneratePrReviewRequest {

    @NotBlank(message = "badgeToken is required")
    private String badgeToken;
}
