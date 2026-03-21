package com.skillproof.backend_core.dto.response;



import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerificationStartResponse {
    private Long sessionId;
    private String repoName;
    private String repoOwner;
    private String repoDescription;
    private String primaryLanguage;
    private List<String> frameworksDetected;
    private Integer filesAnalyzed;
    private List<QuestionDto> questions;
    private String status;
}
