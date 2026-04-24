package com.skillproof.backend_core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.response.NotificationResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Notification;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.NotificationRepository;
import com.skillproof.backend_core.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationEmailDispatcher notificationEmailDispatcher;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public Notification createNotification(Long recipientUserId,
                                           Long senderUserId,
                                           String type,
                                           String title,
                                           String message,
                                           String actionUrl,
                                           Map<String, Object> metadata) {
        User recipient = userRepository.findById(recipientUserId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Recipient user not found"));

        User sender = senderUserId == null
            ? null
            : userRepository.findById(senderUserId).orElse(null);

        Notification notification = Notification.builder()
            .recipientUser(recipient)
            .senderUser(sender)
            .type(safe(type))
            .title(safe(title))
            .message(safe(message))
            .actionUrl(toAbsoluteUrl(actionUrl))
            .metadataJson(writeMetadata(metadata))
            .build();

        Notification saved = notificationRepository.save(notification);
        notificationEmailDispatcher.sendEmailNotification(saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForUser(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, userId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "NOTIFICATION_NOT_FOUND",
                "Notification not found"
            ));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadForRecipient(userId, LocalDateTime.now());
    }

    @Transactional
    public void notifyQuickChallengeSent(Long candidateId, Long recruiterId, String challengeToken, String repoName) {
        createNotification(
            candidateId,
            recruiterId,
            "QUICK_CHALLENGE_SENT",
            "Quick challenge received",
            "A recruiter sent you a quick challenge for " + safe(repoName) + ".",
            "/quick-challenge/" + safe(challengeToken),
            Map.of("challengeToken", safe(challengeToken), "repoName", safe(repoName))
        );
    }

    @Transactional
    public void notifyPrReviewSent(Long candidateId, Long recruiterId, String reviewToken, String repoName) {
        createNotification(
            candidateId,
            recruiterId,
            "PR_REVIEW_SENT",
            "PR review challenge received",
            "A recruiter sent you a PR review challenge for " + safe(repoName) + ".",
            "/pr-review/" + safe(reviewToken),
            Map.of("reviewToken", safe(reviewToken), "repoName", safe(repoName))
        );
    }

    @Transactional
    public void notifyChallengeCompleted(Long recruiterId,
                                         String candidateUsername,
                                         String challengeToken,
                                         Integer score) {
        createNotification(
            recruiterId,
            null,
            "CHALLENGE_COMPLETED",
            "Candidate completed challenge",
            safe(candidateUsername) + " completed a challenge with score " + (score == null ? 0 : score) + "/100.",
            "/recruiter",
            Map.of("candidateUsername", safe(candidateUsername), "challengeToken", safe(challengeToken))
        );
    }

    @Transactional
    public void notifyLiveSessionReady(String candidateUsername, Long recruiterId, String roomUrl) {
        Optional<User> candidate = userRepository.findByGithubUsername(safe(candidateUsername));
        if (candidate.isEmpty()) {
            return;
        }

        createNotification(
            candidate.get().getId(),
            recruiterId,
            "LIVE_SESSION_READY",
            "Live interview room is ready",
            "Your recruiter has opened a live interview room for you.",
            roomUrl,
            Map.of("candidateUsername", safe(candidateUsername), "roomUrl", safe(roomUrl))
        );
    }

    @Transactional
    public void notifyChallengeAssigned(String candidateUsername, Long recruiterId, Long challengeId, String title) {
        Optional<User> candidate = userRepository.findByGithubUsername(safe(candidateUsername));
        if (candidate.isEmpty()) {
            return;
        }

        createNotification(
            candidate.get().getId(),
            recruiterId,
            "CHALLENGE_ASSIGNED",
            "New coding challenge assigned",
            "You received a coding challenge: " + safe(title),
            "/challenge/" + challengeId,
            Map.of("challengeId", challengeId, "candidateUsername", safe(candidateUsername))
        );
    }

    private NotificationResponse toResponse(Notification notification) {
        String senderUsername = notification.getSenderUser() == null
            ? null
            : notification.getSenderUser().getGithubUsername();

        return NotificationResponse.builder()
            .id(notification.getId())
            .type(notification.getType())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .actionUrl(notification.getActionUrl())
            .isRead(notification.getIsRead())
            .createdAt(notification.getCreatedAt())
            .readAt(notification.getReadAt())
            .senderUsername(senderUsername)
            .emailSent(notification.getEmailSent())
            .emailSentAt(notification.getEmailSentAt())
            .metadata(readMetadata(notification.getMetadataJson()))
            .build();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String toAbsoluteUrl(String actionUrl) {
        String normalized = safe(actionUrl);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("/")) {
            return frontendUrl + normalized;
        }
        return frontendUrl + "/" + normalized;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException ex) {
            return new HashMap<>();
        }
    }
}
