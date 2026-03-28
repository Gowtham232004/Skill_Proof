package com.skillproof.backend_core.controller;



import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Answer;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.AnswerRepository;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.UserRepository;
import com.skillproof.backend_core.service.BadgeService;

import org.springframework.http.HttpStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
@Slf4j
public class RecruiterController {

    private final BadgeRepository badgeRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final BadgeService badgeService;
    private final com.skillproof.backend_core.service.AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper;

    private static final Set<User.Role> RECRUITER_ROLES = Set.of(
        User.Role.RECRUITER,
        User.Role.COMPANY,
        User.Role.ADMIN
    );

    /**
     * GET /api/recruiter/candidates
     * Returns all verified candidates with their scores.
     * Used by the recruiter dashboard.
     */
    @GetMapping("/candidates")
    public ResponseEntity<List<Map<String, Object>>> getCandidates(
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        ensureRecruiterRole(userId);
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
                c.put("overallScore", safeInt(b.getOverallScore()));
                c.put("technicalScore", safeInt(b.getTechnicalScore() != null ? b.getTechnicalScore() : b.getOverallScore()));
                c.put("integrityAdjustedScore", safeInt(b.getIntegrityAdjustedScore() != null ? b.getIntegrityAdjustedScore() : b.getOverallScore()));
                c.put("integrityPenaltyTotal", safeInt(b.getIntegrityPenaltyTotal()));
                BadgeResponse candidateDetail = badgeService.getBadgeByToken(b.getVerificationToken());
                Map<String, Integer> scoreByType = candidateDetail.getScoreByQuestionType() != null
                    ? candidateDetail.getScoreByQuestionType()
                    : Map.of();
                c.put("scoreByQuestionType", scoreByType);
                int codeScore = scoreByType.getOrDefault("CODE_GROUNDED", 0);
                int conceptScore = scoreByType.getOrDefault("CONCEPTUAL", 0);
                c.put("conceptGapFlag", codeScore - conceptScore >= 15);
                c.put("weightedScoringEnabled", Boolean.TRUE.equals(candidateDetail.getWeightedScoringEnabled()));
                c.put("codeWeightPercent", safeInt(candidateDetail.getCodeWeightPercent()));
                c.put("conceptualWeightPercent", safeInt(candidateDetail.getConceptualWeightPercent()));
                c.put("followUpRequiredCount", safeInt(candidateDetail.getFollowUpRequiredCount()));
                c.put("followUpAnsweredCount", safeInt(candidateDetail.getFollowUpAnsweredCount()));
                c.put("backendScore", safeInt(b.getBackendScore()));
                c.put("apiDesignScore", safeInt(b.getApiDesignScore()));
                c.put("errorHandlingScore", safeInt(b.getErrorHandlingScore()));
                c.put("codeQualityScore", safeInt(b.getCodeQualityScore()));
                c.put("documentationScore", safeInt(b.getDocumentationScore()));
                c.put("confidenceTier", b.getConfidenceTier());
                c.put("tabSwitches", safeInt(b.getTabSwitches()));
                c.put("pasteCount", safeInt(b.getPasteCount()));
                c.put("avgAnswerSeconds", safeInt(b.getAvgAnswerSeconds()));
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

    @GetMapping("/candidates/{badgeToken}")
    public ResponseEntity<BadgeResponse> getCandidateDetail(
            @PathVariable String badgeToken,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        ensureRecruiterRole(userId);
        log.info("Recruiter {} fetching candidate detail for token {}", userId, badgeToken);

        BadgeResponse response = badgeService.getBadgeByTokenForRecruiter(badgeToken);
        if (!response.isValid()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/candidates/{badgeToken}/questions/{questionNumber}/answer")
    public ResponseEntity<Map<String, Object>> revealCandidateAnswer(
            @PathVariable String badgeToken,
            @PathVariable Integer questionNumber,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        User user = ensureRecruiterRole(userId);

        Badge badge = badgeRepository.findByVerificationToken(badgeToken)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "BADGE_NOT_FOUND",
                "Badge not found"
            ));

        Answer answer = answerRepository
            .findByQuestionSessionIdAndQuestionQuestionNumber(badge.getSession().getId(), questionNumber)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "ANSWER_NOT_FOUND",
                "Answer not found for this question"
            ));

        Map<String, Object> payload = new HashMap<>();
        payload.put("badgeToken", badgeToken);
        payload.put("questionNumber", questionNumber);
        payload.put("fullAnswerText", answer.getAnswerText() == null ? "" : answer.getAnswerText());
        payload.put("revealedBy", user.getGithubUsername());
        payload.put("revealedAt", java.time.LocalDateTime.now().toString());

        log.info("Recruiter {} revealed full answer for badge {} question {}",
            user.getGithubUsername(), badgeToken, questionNumber);

        return ResponseEntity.ok(payload);
    }

    @GetMapping("/candidates/{badgeToken}/questions/{questionNumber}/reference-answer")
    public ResponseEntity<Map<String, Object>> getReferenceAnswer(
            @PathVariable String badgeToken,
            @PathVariable Integer questionNumber,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        ensureRecruiterRole(userId);

        Badge badge = badgeRepository.findByVerificationToken(badgeToken)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "BADGE_NOT_FOUND",
                "Badge not found"
            ));

        Answer answer = answerRepository
            .findByQuestionSessionIdAndQuestionQuestionNumber(badge.getSession().getId(), questionNumber)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "ANSWER_NOT_FOUND",
                "Answer not found for this question"
            ));

        boolean hasCachedAnswer = answer.getReferenceAnswer() != null
            && !answer.getReferenceAnswer().isBlank();
        if (hasCachedAnswer && !refresh) {
            log.info("Reference answer cache hit for badge {} question {}", badgeToken, questionNumber);
            Map<String, Object> cachedPayload = new HashMap<>();
            cachedPayload.put("badgeToken", badgeToken);
            cachedPayload.put("questionNumber", questionNumber);
            cachedPayload.put("referenceAnswer", answer.getReferenceAnswer());
            cachedPayload.put("reviewCheckpoints", parseReviewCheckpoints(answer.getReviewCheckpointsJson()));
            cachedPayload.put("generatedAt", answer.getReferenceAnswerGeneratedAt() != null ? answer.getReferenceAnswerGeneratedAt().toString() : null);
            cachedPayload.put("cached", true);
            cachedPayload.put("status", "success");
            return ResponseEntity.ok(cachedPayload);
        }

        log.info("Reference answer cache miss for badge {} question {} (refresh={})", badgeToken, questionNumber, refresh);
        Map<String, Object> aiResult = aiGatewayService.generateReferenceAnswer(
            answer.getQuestion().getQuestionText(),
            answer.getQuestion().getFileReference(),
            answer.getQuestion().getCodeContext()
        );

        String referenceAnswer = String.valueOf(aiResult.getOrDefault("referenceAnswer", "")).trim();
        @SuppressWarnings("unchecked")
        List<Object> reviewCheckpointsRaw = aiResult.get("reviewCheckpoints") instanceof List<?> list
            ? (List<Object>) list
            : List.of();
        List<String> reviewCheckpoints = reviewCheckpointsRaw.stream()
            .map(item -> String.valueOf(item).trim())
            .filter(text -> !text.isBlank())
            .toList();

        answer.setReferenceAnswer(referenceAnswer);
        answer.setReviewCheckpointsJson(writeReviewCheckpoints(reviewCheckpoints));
        answer.setReferenceAnswerGeneratedAt(java.time.LocalDateTime.now());
        answerRepository.save(answer);

        Map<String, Object> payload = new HashMap<>();
        payload.put("badgeToken", badgeToken);
        payload.put("questionNumber", questionNumber);
        payload.put("referenceAnswer", referenceAnswer);
        payload.put("reviewCheckpoints", reviewCheckpoints);
        payload.put("generatedAt", answer.getReferenceAnswerGeneratedAt().toString());
        payload.put("cached", false);
        payload.put("status", "success");

        return ResponseEntity.ok(payload);
    }

    private List<String> parseReviewCheckpoints(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            log.warn("Could not parse cached review checkpoints JSON", ex);
            return List.of();
        }
    }

    private String writeReviewCheckpoints(List<String> reviewCheckpoints) {
        try {
            return objectMapper.writeValueAsString(reviewCheckpoints);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize review checkpoints JSON", ex);
            return "[]";
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private User ensureRecruiterRole(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User not found"
            ));

        if (!RECRUITER_ROLES.contains(user.getRole())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "RECRUITER_ROLE_REQUIRED",
                "Recruiter role required"
            );
        }
        return user;
    }
}