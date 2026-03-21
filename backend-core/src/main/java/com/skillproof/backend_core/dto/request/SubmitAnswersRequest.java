package com.skillproof.backend_core.dto.request;




import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitAnswersRequest {

    @NotNull
    @JsonProperty("sessionId")
    private Long sessionId;

    @NotEmpty
    @JsonProperty("answers")
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {

        @NotNull
        @JsonProperty("questionId")
        private Long questionId;

        @NotEmpty
        @JsonProperty("answerText")
        private String answerText;
    }
}