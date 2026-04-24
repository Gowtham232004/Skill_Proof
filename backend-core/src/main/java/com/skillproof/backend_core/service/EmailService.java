package com.skillproof.backend_core.service;

public interface EmailService {

    void sendHtmlEmail(String to, String subject, String htmlBody);
}
