package com.skillproof.backend_core.controller;



import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.service.BadgeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/badge")
@RequiredArgsConstructor
@Slf4j
public class BadgeController {

    private final BadgeService badgeService;

    // GET /api/badge/{token}
    // PUBLIC — no auth required
    // This is what recruiters see when a developer shares their badge URL
    @GetMapping("/{token}")
    public ResponseEntity<BadgeResponse> getBadge(@PathVariable String token) {
        log.info("Badge fetch request for token: {}", token);
        BadgeResponse response = badgeService.getBadgeByToken(token);

        if (!response.isValid()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}