package com.skillproof.backend_core.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitFollowUpAnswersRequest {

    @NotNull
    @JsonProperty("sessionId")
    private Long sessionId;

    @NotEmpty
    @JsonProperty("followUps")
    private List<FollowUpAnswerItem> followUps;

    @Data
    public static class FollowUpAnswerItem {
        @NotNull
        @JsonProperty("questionNumber")
        private Integer questionNumber;

        @NotNull
        @JsonProperty("followUpQuestion")
        private String followUpQuestion;

        @JsonProperty("answerText")
        private String answerText;

        @JsonProperty("skipped")
        private Boolean skipped;
    }
}
