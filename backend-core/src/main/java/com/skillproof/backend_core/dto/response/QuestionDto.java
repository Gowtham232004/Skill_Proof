package com.skillproof.backend_core.dto.response;



import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionDto {
    private Long id;
    private Integer questionNumber;
    private String difficulty;
    private String questionType;
    private String fileReference;
    private String questionText;
    private String codeContextSnippet;
}