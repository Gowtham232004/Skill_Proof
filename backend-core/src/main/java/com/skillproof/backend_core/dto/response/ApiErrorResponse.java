package com.skillproof.backend_core.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiErrorResponse {

    private String code;
    private String message;
    private Map<String, Object> details;
    private LocalDateTime timestamp;
}
