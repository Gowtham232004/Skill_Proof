package com.skillproof.backend_core.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveAnswerSubmitResponse {
    private String sessionCode;
    private Integer questionNumber;
    private Integer accuracyScore;
    private Integer depthScore;
    private Integer specificityScore;
    private Integer compositeScore;
    private String aiFeedback;
    private Boolean allQuestionsAnswered;
    private Integer overallLiveScore;
}
