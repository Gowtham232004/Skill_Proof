package com.skillproof.backend_core.service;



import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final HmacUtil hmacUtil;
    private final ObjectMapper objectMapper;

    private static final String BADGE_BASE_URL = "http://localhost:3000/badge/";

    // Create a new badge after verification completes
    public Badge createBadge(VerificationSession session,
                              Integer overallScore,
                              Integer backendScore,
                              Integer apiDesignScore,
                              Integer errorHandlingScore,
                              Integer codeQualityScore,
                              Integer documentationScore) {

        long timestamp = System.currentTimeMillis();
        String token = hmacUtil.generateBadgeToken(
            session.getUser().getId(),
            session.getRepoName(),
            overallScore,
            timestamp
        );

        Badge badge = Badge.builder()
            .session(session)
            .user(session.getUser())
            .verificationToken(token)
            .overallScore(overallScore)
            .backendScore(backendScore)
            .apiDesignScore(apiDesignScore)
            .errorHandlingScore(errorHandlingScore)
            .codeQualityScore(codeQualityScore)
            .documentationScore(documentationScore)
            .isActive(true)
            .build();

        badge = badgeRepository.save(badge);
        log.info("Badge created: token={}, score={}", token, overallScore);
        return badge;
    }

    // Get public badge data for the badge page
    public BadgeResponse getBadgeByToken(String token) {
        Badge badge = badgeRepository.findByVerificationToken(token)
            .orElse(null);

        if (badge == null || !badge.getIsActive()) {
            return BadgeResponse.builder()
                .valid(false)
                .verificationToken(token)
                .build();
        }

        VerificationSession session = badge.getSession();

        // Parse frameworks from JSON string
        List<String> frameworks = List.of();
        try {
            if (session.getFrameworksDetected() != null) {
                frameworks = objectMapper.readValue(
                    session.getFrameworksDetected(),
                    new TypeReference<List<String>>() {}
                );
            }
        } catch (Exception e) {
            log.warn("Could not parse frameworks for badge {}", token);
        }

        return BadgeResponse.builder()
            .valid(true)
            .verificationToken(token)
            .badgeUrl(BADGE_BASE_URL + token)
            .githubUsername(badge.getUser().getGithubUsername())
            .avatarUrl(badge.getUser().getAvatarUrl())
            .displayName(badge.getUser().getDisplayName())
            .repoName(session.getRepoName())
            .repoOwner(session.getRepoOwner())
            .repoDescription(session.getRepoDescription())
            .primaryLanguage(session.getRepoLanguage())
            .frameworksDetected(frameworks)
            .overallScore(badge.getOverallScore())
            .backendScore(badge.getBackendScore())
            .apiDesignScore(badge.getApiDesignScore())
            .errorHandlingScore(badge.getErrorHandlingScore())
            .codeQualityScore(badge.getCodeQualityScore())
            .documentationScore(badge.getDocumentationScore())
            .issuedAt(badge.getIssuedAt())
            .build();
    }
}