package com.skillproof.backend_core.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillproof.backend_core.dto.request.CreateVideoRoomRequest;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.service.NotificationService;
import com.skillproof.backend_core.service.VideoRoomService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoRoomService videoRoomService;
    private final NotificationService notificationService;

    @PostMapping("/create-room")
    public ResponseEntity<Map<String, String>> createRoom(
            @RequestBody CreateVideoRoomRequest request,
            Authentication authentication) {

        Long recruiterId = extractAuthenticatedUserId(authentication);
        String candidate = request == null ? "interview" : safe(request.getCandidateUsername());
        String roomName = candidate + "-" + Instant.now().getEpochSecond();
        Map<String, String> room = videoRoomService.createRoom(roomName);
        notificationService.notifyLiveSessionReady(candidate, recruiterId, room.getOrDefault("url", ""));
        return ResponseEntity.ok(room);
    }

    private Long extractAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number n) {
            return n.longValue();
        }

        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid authenticated principal");
    }

    private String safe(String candidateUsername) {
        String normalized = candidateUsername == null ? "interview" : candidateUsername.trim();
        return normalized.isBlank() ? "interview" : normalized;
    }
}
