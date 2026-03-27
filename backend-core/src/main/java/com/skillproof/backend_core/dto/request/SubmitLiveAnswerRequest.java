package com.skillproof.backend_core.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitLiveAnswerRequest {

    @NotBlank(message = "answerText is required")
    private String answerText;
}
