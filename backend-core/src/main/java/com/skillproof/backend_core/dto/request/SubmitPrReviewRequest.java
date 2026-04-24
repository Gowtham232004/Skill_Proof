package com.skillproof.backend_core.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitPrReviewRequest {

    @Valid
    private List<ReviewCommentInput> comments;

    private Integer timeTaken;

    @Getter
    @Setter
    public static class ReviewCommentInput {
        private Integer lineNumber;
        private String comment;
        private String severity;
    }
}
