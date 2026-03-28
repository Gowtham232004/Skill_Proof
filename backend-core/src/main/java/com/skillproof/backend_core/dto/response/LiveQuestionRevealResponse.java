package com.skillproof.backend_core.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveQuestionRevealResponse {
    private String sessionCode;
    private Integer questionNumber;
    private Integer totalQuestions;
    private String questionText;
    private String difficulty;
    private String fileReference;
    private String codeContext;
    private Boolean isLastQuestion;
}
