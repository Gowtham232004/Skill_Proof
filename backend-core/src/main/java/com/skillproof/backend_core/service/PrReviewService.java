package com.skillproof.backend_core.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.SubmitPrReviewRequest;
import com.skillproof.backend_core.dto.response.PrReviewResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.PrReview;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.PrReviewRepository;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrReviewService {

    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjklmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern REPEATED_NOISE_PATTERN = Pattern.compile("(.{1,2})\\1{4,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_PHRASE_PATTERN = Pattern.compile("\\b(error is here|big error|issue here|wrong here|bug here)\\b", Pattern.CASE_INSENSITIVE);

    private final PrReviewRepository prReviewRepository;
    private final BadgeRepository badgeRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Transactional
    public PrReviewResponse generateReview(Long recruiterId, String badgeToken) {
        // Retrieve the badge with associated user and session data
        Badge badge = badgeRepository.findByVerificationTokenWithUserAndSession(badgeToken)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BADGE_NOT_FOUND", "Badge not found"));

        // Verify recruiter exists and is authorized
        User recruiter = userRepository.findById(recruiterId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Recruiter not found"));

        // Check for existing active PR review to avoid duplicate challenges
        List<PrReview> previous = prReviewRepository.findByBadgeTokenAndRecruiterIdOrderByCreatedAtDesc(
            badgeToken,
            recruiterId
        );

        PrReview active = previous.stream()
            .filter(r -> r.getStatus() == PrReview.PrReviewStatus.PENDING || r.getStatus() == PrReview.PrReviewStatus.ACTIVE)
            .filter(r -> r.getExpiresAt() != null && LocalDateTime.now().isBefore(r.getExpiresAt()))
            .findFirst()
            .orElse(null);
        if (active != null) {
            log.info("Reusing active PR review {} for badge {} and recruiter {}", active.getReviewToken(), badgeToken, recruiterId);
            return buildResponse(active, false, true);
        }

        // Fetch all questions for the session to find suitable code context for review
        List<Question> questions = questionRepository.findBySessionIdOrderByQuestionNumber(badge.getSession().getId());
        List<Question> candidateQuestions = questions.stream()
            .filter(q -> q.getCodeContext() != null && q.getCodeContext().length() > 120)
            .toList();
        if (candidateQuestions.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INSUFFICIENT_CODE_CONTEXT",
                "Insufficient code context for PR review challenge"
            );
        }

        // Track previously used files to ensure variety in subsequent reviews
        Set<String> previouslyUsedFiles = new HashSet<>();
        for (PrReview review : previous) {
            String used = normalize(review.getFilePath());
            if (!used.isBlank()) {
                previouslyUsedFiles.add(used);
            }
        }

        List<Question> unseenQuestions = candidateQuestions.stream()
            .filter(q -> !previouslyUsedFiles.contains(normalize(q.getFileReference())))
            .toList();

        List<Question> pool = unseenQuestions.isEmpty() ? candidateQuestions : unseenQuestions;
        Question selectedQuestion = pool.get(RANDOM.nextInt(pool.size()));

        String filePath = safe(selectedQuestion.getFileReference());
        String language = badge.getSession().getRepoLanguage() == null
            ? "JAVA"
            : badge.getSession().getRepoLanguage();

        Map<String, Object> bugResult = aiGatewayService.generateCodeBug(
            safe(selectedQuestion.getCodeContext()),
            filePath,
            language
        );

        if (bugResult.isEmpty() || bugResult.containsKey("error")) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PR_REVIEW_AI_FAILED",
                "Failed to generate code review challenge. Try again."
            );
        }

        String modifiedCode = sanitizeModifiedCodeForCandidate(safeString(bugResult.get("modified_code")));
        if (modifiedCode.isBlank()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PR_REVIEW_AI_INVALID",
                "AI returned an invalid PR review payload"
            );
        }

        PrReview review = new PrReview();
        review.setBadgeToken(badgeToken);
        review.setRecruiter(recruiter);
        review.setFilePath(filePath);
        review.setOriginalCode(safe(selectedQuestion.getCodeContext()));
        review.setModifiedCode(modifiedCode);
        review.setBugDescription(safeString(bugResult.get("bug_description")));
        review.setCandidateUsername(badge.getUser().getGithubUsername());
        review.setRepoName(badge.getSession().getRepoName());
        review.setReviewToken(generateToken());
        review.setCreatedAt(LocalDateTime.now());
        review.setExpiresAt(LocalDateTime.now().plusHours(48));

        PrReview saved = prReviewRepository.save(review);
        notificationService.notifyPrReviewSent(
            badge.getUser().getId(),
            recruiterId,
            saved.getReviewToken(),
            saved.getRepoName()
        );
        log.info("PR review {} created for badge {} by recruiter {}", saved.getReviewToken(), badgeToken, recruiterId);
        return buildResponse(saved, false, true);
    }

    public PrReviewResponse openReview(String reviewToken) {
        PrReview review = findValid(reviewToken);
        if (review.getStatus() == PrReview.PrReviewStatus.PENDING) {
            review.setStatus(PrReview.PrReviewStatus.ACTIVE);
            prReviewRepository.save(review);
        }
        // Return candidate view: no pre-loaded comments from backend, show modified code
        return buildResponse(review, false, true, false);
    }

    public PrReviewResponse submitReview(String reviewToken,
                                         List<SubmitPrReviewRequest.ReviewCommentInput> comments,
                                         Integer timeTakenSeconds) {
        // Validate review exists and hasn't already been submitted or expired
        PrReview review = findValid(reviewToken);
        if (review.getStatus() == PrReview.PrReviewStatus.COMPLETED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "PR_REVIEW_ALREADY_SUBMITTED",
                "This review has already been submitted"
            );
        }

        // Normalize and validate comments - allow empty submission on timeout
        List<Map<String, Object>> normalizedComments = normalizeComments(comments);
        // Comments are optional - candidate may skip or auto-submit on timeout without comments
        try {
            review.setCandidateReviewJson(objectMapper.writeValueAsString(normalizedComments));
        } catch (JsonProcessingException ex) {
            review.setCandidateReviewJson("[]");
        }

        // Format comments for AI evaluation and get scoring feedback
        String allComments = formatCommentsForEval(normalizedComments);
        Map<String, Object> evalResult = aiGatewayService.evaluatePrReview(
            safe(review.getOriginalCode()),
            safe(review.getModifiedCode()),
            safe(review.getBugDescription()),
            allComments
        );

        // Store evaluation results and mark review as completed
        review.setOverallScore(getInt(evalResult, "score"));
        review.setBugsFoundCount(getInt(evalResult, "bugs_identified"));
        review.setAiFeedback(safeString(evalResult.get("feedback")));
        review.setTimeTakenSeconds(timeTakenSeconds == null ? 0 : Math.max(0, timeTakenSeconds));
        review.setStatus(PrReview.PrReviewStatus.COMPLETED);
        review.setCompletedAt(LocalDateTime.now());

        PrReview saved = prReviewRepository.save(review);
        notificationService.notifyChallengeCompleted(
            saved.getRecruiter().getId(),
            saved.getCandidateUsername(),
            saved.getReviewToken(),
            saved.getOverallScore()
        );
        log.info("PR review {} submitted with score {}", reviewToken, saved.getOverallScore());
        return buildResponse(saved, false, true);
    }

    public PrReviewResponse getResult(Long recruiterId, String reviewToken) {
        PrReview review = prReviewRepository.findByReviewToken(reviewToken)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PR_REVIEW_NOT_FOUND", "Review not found"));

        if (!Objects.equals(review.getRecruiter().getId(), recruiterId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PR_REVIEW_FORBIDDEN", "Access denied");
        }

        return buildResponse(review, true, true);
    }

    private PrReview findValid(String token) {
        PrReview review = prReviewRepository.findByReviewToken(token)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PR_REVIEW_NOT_FOUND", "Review not found"));

        boolean expired = LocalDateTime.now().isAfter(review.getExpiresAt());
        boolean complete = review.getStatus() == PrReview.PrReviewStatus.COMPLETED;
        if (expired && !complete) {
            review.setStatus(PrReview.PrReviewStatus.EXPIRED);
            prReviewRepository.save(review);
            throw new ApiException(HttpStatus.GONE, "PR_REVIEW_EXPIRED", "Review challenge expired");
        }
        return review;
    }

    private List<Map<String, Object>> normalizeComments(List<SubmitPrReviewRequest.ReviewCommentInput> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (SubmitPrReviewRequest.ReviewCommentInput comment : comments) {
            if (comment == null) {
                continue;
            }
            String text = safe(comment.getComment()).trim();
            if (text.isBlank()) {
                continue;
            }
            Integer lineNumber = comment.getLineNumber() == null ? 0 : Math.max(0, comment.getLineNumber());
            if (!isMeaningfulComment(text, lineNumber)) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("lineNumber", lineNumber);
            item.put("comment", text);
            item.put("severity", normalizeSeverity(comment.getSeverity()));
            normalized.add(item);
        }
        return normalized;
    }

    private String normalizeSeverity(String severity) {
        String normalized = safe(severity).trim().toUpperCase();
        if ("CRITICAL".equals(normalized) || "IMPORTANT".equals(normalized)) {
            return normalized;
        }
        return "MINOR";
    }

    private String formatCommentsForEval(List<Map<String, Object>> comments) {
        if (comments.isEmpty()) {
            return "[No comments provided]";
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> c : comments) {
            sb.append("Line ")
                .append(c.getOrDefault("lineNumber", "?"))
                .append(" [")
                .append(c.getOrDefault("severity", "MINOR"))
                .append("]: ")
                .append(c.getOrDefault("comment", ""))
                .append("\n");
        }
        return sb.toString();
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(TOKEN_CHARS.charAt(random.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }

    private PrReviewResponse buildResponse(PrReview review, boolean includeRecruiterFields, boolean includeCandidateCode) {
        return buildResponse(review, includeRecruiterFields, includeCandidateCode, includeRecruiterFields);
    }

    private PrReviewResponse buildResponse(PrReview review, boolean includeRecruiterFields, boolean includeCandidateCode, boolean includeCandidateComments) {
        return PrReviewResponse.builder()
            .id(review.getId())
            .reviewToken(review.getReviewToken())
            .badgeToken(review.getBadgeToken())
            .candidateUsername(review.getCandidateUsername())
            .repoName(review.getRepoName())
            .filePath(review.getFilePath())
            .modifiedCode(includeCandidateCode ? review.getModifiedCode() : null)
            .status(review.getStatus().name())
            .overallScore(review.getOverallScore())
            .bugsFoundCount(review.getBugsFoundCount())
            .aiFeedback(review.getAiFeedback())
            .timeTakenSeconds(review.getTimeTakenSeconds())
            .createdAt(review.getCreatedAt())
            .expiresAt(review.getExpiresAt())
            .completedAt(review.getCompletedAt())
            .candidateUrl("/pr-review/" + review.getReviewToken())
            .bugDescription(includeRecruiterFields ? review.getBugDescription() : null)
            .originalCode(includeRecruiterFields ? review.getOriginalCode() : null)
            .comments(includeCandidateComments ? parseComments(review.getCandidateReviewJson()) : null)
            .build();
    }

    private List<PrReviewResponse.ReviewComment> parseComments(String candidateReviewJson) {
        if (candidateReviewJson == null || candidateReviewJson.isBlank()) {
            return List.of();
        }

        try {
            List<Map<String, Object>> raw = objectMapper.readValue(candidateReviewJson, new TypeReference<>() { });
            List<PrReviewResponse.ReviewComment> parsed = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                parsed.add(PrReviewResponse.ReviewComment.builder()
                    .lineNumber(getInt(item, "lineNumber"))
                    .comment(safeString(item.get("comment")))
                    .severity(safeString(item.get("severity")))
                    .build());
            }
            return parsed;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private int getInt(Map<String, Object> map, String key) {
        return getInt(map.get(key));
    }

    private int getInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String sanitizeModifiedCodeForCandidate(String modifiedCode) {
        if (modifiedCode == null || modifiedCode.isBlank()) {
            return "";
        }
        
        // Remove internal markers but keep the code with bugs intact
        return modifiedCode
            .lines()
            .map(line -> {
                // Keep the line but strip internal "BUG INTRODUCED HERE" markers from candidate view
                if (line.toLowerCase().contains("bug introduced here")) {
                    return line.replaceAll("(?i).*bug\\s+introduced\\s+here.*", "");
                }
                return line;
            })
            .filter(line -> !line.trim().isEmpty())
            .collect(Collectors.joining("\n"));
    }

    private String addTechnicalComments(String originalCode, String modifiedCode, String bugDescription) {
        if (modifiedCode == null || modifiedCode.isBlank() || bugDescription == null || bugDescription.isBlank()) {
            return modifiedCode;
        }

        StringBuilder result = new StringBuilder();
        String[] lines = modifiedCode.split("\n");
        
        // Add a header comment explaining the bug area
        result.append("// REVIEW CONTEXT:\n");
        result.append("// This code contains intentional issues that need to be identified and explained.\n");
        result.append("// Focus on understanding the logic and spotting problems.\n");
        result.append("\n");
        
        // Note: originalCode parameter is kept for potential future context-diff features
        result.append(String.join("\n", lines));
        
        return result.toString();
    }

    private boolean isMeaningfulComment(String text, int lineNumber) {
        String normalized = text == null ? "" : text.trim().toLowerCase();
        if (lineNumber <= 0 || normalized.length() < 18) {
            return false;
        }
        if (REPEATED_NOISE_PATTERN.matcher(normalized).find()) {
            return false;
        }
        if (GENERIC_PHRASE_PATTERN.matcher(normalized).find()) {
            return false;
        }
        if (normalized.contains("asdf") || normalized.contains("dfad") || normalized.contains("qwerty") || normalized.contains("lorem")) {
            return false;
        }

        String[] tokens = normalized.split("\\s+");
        int meaningfulWords = 0;
        for (String token : tokens) {
            if (token.matches("[a-z_]{3,}[a-z0-9_]*")) {
                meaningfulWords++;
            }
        }
        if (meaningfulWords < 4) {
            return false;
        }

        boolean hasIssueSignal = normalized.matches(".*\\b(null|exception|invalid|wrong|fails|error|unsafe|mismatch|bug)\\b.*");
        boolean hasFixSignal = normalized.matches(".*\\b(should|must|replace|change|handle|throw|return|check|validate)\\b.*");
        return hasIssueSignal && hasFixSignal;
    }
}
