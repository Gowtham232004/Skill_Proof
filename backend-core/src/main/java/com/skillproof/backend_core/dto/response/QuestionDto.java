package com.skillproof.backend_core.dto.response;



import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionDto {
    private Long id;
    private Integer questionNumber;
    private String difficulty;
    private String fileReference;
    private String questionText;
    // NOTE: codeContext is NOT included in response — security
    // Developer should not see what code the question came from
}