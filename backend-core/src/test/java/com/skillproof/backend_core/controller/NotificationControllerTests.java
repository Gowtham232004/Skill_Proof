package com.skillproof.backend_core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.skillproof.backend_core.dto.response.NotificationResponse;
import com.skillproof.backend_core.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTests {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Authentication authentication;

    @Test
    void getUnreadCountReturnsServiceValue() {
        NotificationController controller = new NotificationController(notificationService);

        when(authentication.getPrincipal()).thenReturn(9L);
        when(notificationService.getUnreadCount(9L)).thenReturn(4L);

        ResponseEntity<Map<String, Object>> response = controller.getUnreadCount(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(4L, response.getBody().get("unreadCount"));
    }

    @Test
    void getNotificationsReturnsRowsFromService() {
        NotificationController controller = new NotificationController(notificationService);

        NotificationResponse row = NotificationResponse.builder()
            .id(22L)
            .type("QUICK_CHALLENGE_SENT")
            .title("Quick challenge received")
            .message("A recruiter sent you a challenge")
            .actionUrl("http://localhost:3000/quick-challenge/token")
            .isRead(false)
            .createdAt(LocalDateTime.now())
            .build();

        when(authentication.getPrincipal()).thenReturn(9L);
        when(notificationService.getNotificationsForUser(9L)).thenReturn(List.of(row));

        ResponseEntity<List<NotificationResponse>> response = controller.getNotifications(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("QUICK_CHALLENGE_SENT", response.getBody().get(0).getType());
    }
}
