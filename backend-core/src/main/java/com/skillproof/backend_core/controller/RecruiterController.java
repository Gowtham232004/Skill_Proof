package com.skillproof.backend_core.controller;



import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.repository.BadgeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
@Slf4j
public class RecruiterController {

    private final BadgeRepository badgeRepository;

    /**
     * GET /api/recruiter/candidates
     * Returns all verified candidates with their scores.
     * Used by the recruiter dashboard.
     */
    @GetMapping("/candidates")
    public ResponseEntity<List<Map<String, Object>>> getCandidates(
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        log.info("Recruiter {} fetching all candidates", userId);

        List<Badge> badges = badgeRepository.findAll();

        List<Map<String, Object>> result = badges.stream()
            .filter(b -> b.getIsActive() != null && b.getIsActive())
            .map(b -> {
                Map<String, Object> c = new HashMap<>();
                c.put("sessionId", b.getId());
                c.put("githubUsername", b.getUser().getGithubUsername());
                c.put("avatarUrl", b.getUser().getAvatarUrl());
                c.put("displayName", b.getUser().getDisplayName() != null
                    ? b.getUser().getDisplayName()
                    : b.getUser().getGithubUsername());
                c.put("repoName", b.getSession().getRepoName());
                c.put("repoOwner", b.getSession().getRepoOwner());
                c.put("overallScore", b.getOverallScore() != null ? b.getOverallScore() : 0);
                c.put("backendScore", b.getBackendScore() != null ? b.getBackendScore() : 0);
                c.put("apiDesignScore", b.getApiDesignScore() != null ? b.getApiDesignScore() : 0);
                c.put("errorHandlingScore", b.getErrorHandlingScore() != null ? b.getErrorHandlingScore() : 0);
                c.put("codeQualityScore", b.getCodeQualityScore() != null ? b.getCodeQualityScore() : 0);
                c.put("documentationScore", b.getDocumentationScore() != null ? b.getDocumentationScore() : 0);
                c.put("badgeToken", b.getVerificationToken());
                c.put("issuedAt", b.getIssuedAt() != null ? b.getIssuedAt().toString() : null);
                c.put("primaryLanguage", b.getSession().getRepoLanguage() != null
                    ? b.getSession().getRepoLanguage() : "Unknown");
                return c;
            })
            .collect(Collectors.toList());

        log.info("Returning {} candidates for recruiter dashboard", result.size());
        return ResponseEntity.ok(result);
    }
}