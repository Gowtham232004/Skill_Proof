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

    @JsonProperty("totalTabSwitches")
    private Integer totalTabSwitches;

    @JsonProperty("pasteCount")
    private Integer pasteCount;

    @JsonProperty("totalCopyEvents")
    private Integer totalCopyEvents;

    @JsonProperty("avgAnswerSeconds")
    private Integer avgAnswerSeconds;

    @Data
    public static class AnswerItem {

        @NotNull
        @JsonProperty("questionId")
        private Long questionId;

        @JsonProperty("answerText")
        private String answerText;

        @JsonProperty("skipped")
        private Boolean skipped;
    }
}