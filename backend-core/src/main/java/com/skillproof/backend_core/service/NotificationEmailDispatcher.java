package com.skillproof.backend_core.service;

import com.skillproof.backend_core.model.Notification;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.NotificationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailDispatcher {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    @Async
    @Transactional
    public void sendEmailNotification(Long notificationId) {
        if (!emailEnabled) {
            return;
        }

        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            return;
        }

        User recipient = notification.getRecipientUser();
        String recipientEmail = recipient == null ? "" : safe(recipient.getEmail());
        if (recipientEmail.isBlank()) {
            return;
        }

        String subject = "SkillProof: " + safe(notification.getTitle());
        String actionUrl = safe(notification.getActionUrl());
        String html = """
            <html><body style='font-family:Arial,sans-serif'>
            <h3>%s</h3>
            <p>%s</p>
            %s
            <p style='font-size:12px;color:#666'>This notification was sent by SkillProof.</p>
            </body></html>
            """.formatted(
            safe(notification.getTitle()),
            safe(notification.getMessage()),
            actionUrl.isBlank() ? "" : "<p><a href='" + actionUrl + "'>Open in SkillProof</a></p>"
        );

        try {
            emailService.sendHtmlEmail(recipientEmail, subject, html);
            notification.setEmailSent(true);
            notification.setEmailSentAt(LocalDateTime.now());
            notificationRepository.save(notification);
        } catch (Exception ex) {
            log.warn("Email send failed for notification {}", notificationId, ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
