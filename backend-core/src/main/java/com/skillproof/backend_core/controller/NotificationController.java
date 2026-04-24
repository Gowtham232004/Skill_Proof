package com.skillproof.backend_core.controller;

import com.skillproof.backend_core.dto.response.NotificationResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.service.NotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(Authentication authentication) {
        Long userId = extractAuthenticatedUserId(authentication);
        return ResponseEntity.ok(notificationService.getNotificationsForUser(userId));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        Long userId = extractAuthenticatedUserId(authentication);
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = extractAuthenticatedUserId(authentication);
        return ResponseEntity.ok(notificationService.markAsRead(userId, id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(Authentication authentication) {
        Long userId = extractAuthenticatedUserId(authentication);
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("updatedCount", updated));
    }

    private Long extractAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }

        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid authenticated principal");
    }
}
