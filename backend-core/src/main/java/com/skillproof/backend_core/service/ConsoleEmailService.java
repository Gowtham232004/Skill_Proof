package com.skillproof.backend_core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
@Slf4j
public class ConsoleEmailService implements EmailService {

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        log.info("[DEV EMAIL] to={} subject={} body={}", to, subject, htmlBody);
    }
}
