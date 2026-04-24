package com.skillproof.backend_core.dto.response;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationResponse {

    private Long id;
    private String type;
    private String title;
    private String message;
    private String actionUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private String senderUsername;
    private Boolean emailSent;
    private LocalDateTime emailSentAt;
    private Map<String, Object> metadata;
}
