package com.skillproof.backend_core.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillproof.backend_core.dto.response.QuickChallengeResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.QuickChallenge;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.QuickChallengeRepository;
import com.skillproof.backend_core.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuickChallengeService {

    private static final int CANDIDATE_TIME_SECONDS = 600;
    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjklmnpqrstuvwxyz23456789";
    private static final Set<User.Role> RECRUITER_ROLES = Set.of(
        User.Role.RECRUITER,
        User.Role.COMPANY,
        User.Role.ADMIN
    );

    private final QuickChallengeRepository quickChallengeRepository;
    private final BadgeRepository badgeRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AiGatewayService aiGatewayService;
    private final NotificationService notificationService;

    @Transactional
    public QuickChallengeResponse generateChallenge(Long recruiterId, String badgeToken) {
        User recruiter = ensureRecruiter(recruiterId);
        String cleanToken = badgeToken == null ? "" : badgeToken.trim();
        if (cleanToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BADGE_TOKEN_REQUIRED", "Badge token is required");
        }

        Badge badge = badgeRepository.findByVerificationToken(cleanToken)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BADGE_NOT_FOUND", "Badge not found"));

        QuickChallenge reusable = findReusableChallenge(recruiter.getId(), cleanToken);
        if (reusable != null) {
            log.info("Quick challenge {} reused for recruiter {}", reusable.getChallengeToken(), recruiterId);
            return buildResponse(reusable, false, false);
        }

        List<Question> questions = questionRepository.findBySessionIdOrderByQuestionNumber(badge.getSession().getId());
        List<Question> candidates = questions.stream()
            .filter(q -> q.getCodeContext() != null && q.getCodeContext().trim().length() > 120)
            .toList();

        Set<String> usedFiles = new HashSet<>();
        for (Question question : questions) {
            String fileRef = normalizeFileRef(question.getFileReference());
            if (!fileRef.isBlank()) {
                usedFiles.add(fileRef);
            }
        }

        List<SnippetSource> summarySources = parseSummarySources(badge.getSession().getCodeSummary());
        List<SnippetSource> unseenSources = summarySources.stream()
            .filter(source -> !usedFiles.contains(normalizeFileRef(source.filePath())))
            .toList();

        String selectedFile = "";
        String snippet = "";

        if (!unseenSources.isEmpty()) {
            SnippetSource source = unseenSources.get(new SecureRandom().nextInt(unseenSources.size()));
            selectedFile = safeText(source.filePath());
            snippet = source.snippet();
        } else if (!summarySources.isEmpty()) {
            SnippetSource source = summarySources.get(new SecureRandom().nextInt(summarySources.size()));
            selectedFile = safeText(source.filePath());
            snippet = source.snippet();
        } else if (!candidates.isEmpty()) {
            Question selectedQuestion = candidates.get(new SecureRandom().nextInt(candidates.size()));
            selectedFile = safeText(selectedQuestion.getFileReference());
            snippet = extractFocusedSnippet(selectedQuestion.getCodeContext());
        }

        if (snippet.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "QUICK_CHALLENGE_SNIPPET_EMPTY",
                "Could not build a usable code snippet from this session"
            );
        }

        String questionText = aiGatewayService.generateSnippetQuestion(
            snippet,
            selectedFile,
            badge.getSession().getRepoLanguage()
        );
        if (questionText == null || questionText.isBlank()) {
            questionText = "Explain what this specific code does, why it is written this way, and one edge case you considered.";
        }

        QuickChallenge challenge = QuickChallenge.builder()
            .badgeToken(cleanToken)
            .recruiter(recruiter)
            .selectedFilePath(selectedFile)
            .codeSnippet(snippet)
            .questionText(questionText)
            .candidateUsername(safeText(badge.getUser().getGithubUsername()))
            .repoName(safeText(badge.getSession().getRepoName()))
            .status(QuickChallenge.QuickChallengeStatus.PENDING)
            .challengeToken(generateToken())
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        QuickChallenge saved = quickChallengeRepository.save(challenge);
        notificationService.notifyQuickChallengeSent(
            badge.getUser().getId(),
            recruiter.getId(),
            saved.getChallengeToken(),
            saved.getRepoName()
        );
        log.info("Quick challenge {} created for recruiter {}", saved.getChallengeToken(), recruiterId);
        return buildResponse(saved, false, false);
    }

    @Transactional
    public QuickChallengeResponse openChallenge(String challengeToken) {
        QuickChallenge challenge = findValidChallenge(challengeToken, false);
        if (challenge.getStatus() == QuickChallenge.QuickChallengeStatus.PENDING) {
            challenge.setStatus(QuickChallenge.QuickChallengeStatus.ACTIVE);
            challenge.setOpenedAt(LocalDateTime.now());
            quickChallengeRepository.save(challenge);
            log.info("Quick challenge {} opened", challenge.getChallengeToken());
        }

        QuickChallenge refreshed = quickChallengeRepository.findById(challenge.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUICK_CHALLENGE_NOT_FOUND", "Challenge not found"));
        return buildResponse(refreshed, true, false);
    }

    @Transactional
    public QuickChallengeResponse submitAnswer(String challengeToken, String answerText, Integer tabSwitchCount, Integer timeTakenSeconds) {
        QuickChallenge challenge = findValidChallenge(challengeToken, true);
        if (challenge.getStatus() == QuickChallenge.QuickChallengeStatus.COMPLETED) {
            return buildResponse(challenge, true, true);
        }

        if (challenge.getStatus() == QuickChallenge.QuickChallengeStatus.PENDING) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "QUICK_CHALLENGE_NOT_OPENED",
                "Challenge must be opened before submission"
            );
        }

        String cleanAnswer = answerText == null ? "" : answerText.trim();
        if (cleanAnswer.length() < 20) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "QUICK_CHALLENGE_ANSWER_TOO_SHORT",
                "Answer must be at least 20 characters"
            );
        }

        Map<String, Object> evalResult = aiGatewayService.evaluateSingleAnswer(
            challenge.getQuestionText(),
            challenge.getSelectedFilePath(),
            challenge.getCodeSnippet(),
            cleanAnswer
        );

        int accuracy = getInt(evalResult, "accuracy_score");
        int depth = getInt(evalResult, "depth_score");
        int specificity = getInt(evalResult, "specificity_score");
        String feedback = String.valueOf(evalResult.getOrDefault("ai_feedback", "")).trim();
        int overall = clamp((accuracy * 4) + (depth * 3) + (specificity * 3));

        challenge.setCandidateAnswer(cleanAnswer);
        challenge.setAccuracyScore(accuracy);
        challenge.setDepthScore(depth);
        challenge.setSpecificityScore(specificity);
        challenge.setOverallScore(overall);
        challenge.setAiFeedback(feedback);
        challenge.setTabSwitchCount(tabSwitchCount == null ? 0 : Math.max(tabSwitchCount, 0));
        challenge.setTimeTakenSeconds(resolveTimeTakenSeconds(challenge, timeTakenSeconds));
        challenge.setStatus(QuickChallenge.QuickChallengeStatus.COMPLETED);
        challenge.setCompletedAt(LocalDateTime.now());

        QuickChallenge saved = quickChallengeRepository.save(challenge);
        notificationService.notifyChallengeCompleted(
            saved.getRecruiter().getId(),
            saved.getCandidateUsername(),
            saved.getChallengeToken(),
            saved.getOverallScore()
        );
        log.info("Quick challenge {} submitted with score {}", saved.getChallengeToken(), overall);
        return buildResponse(saved, true, true);
    }

    @Transactional(readOnly = true)
    public QuickChallengeResponse getChallengeResult(Long recruiterId, String challengeToken) {
        User recruiter = ensureRecruiter(recruiterId);
        QuickChallenge challenge = quickChallengeRepository.findByChallengeToken(challengeToken)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUICK_CHALLENGE_NOT_FOUND", "Challenge not found"));

        if (!Objects.equals(challenge.getRecruiter().getId(), recruiter.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "QUICK_CHALLENGE_FORBIDDEN", "Access denied");
        }

        return buildResponse(challenge, true, true);
    }

    @Transactional(readOnly = true)
    public List<QuickChallengeResponse> getRecruiterChallenges(Long recruiterId) {
        User recruiter = ensureRecruiter(recruiterId);
        return quickChallengeRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiter.getId())
            .stream()
            .map(challenge -> buildResponse(challenge, false, true))
            .toList();
    }

    private User ensureRecruiter(Long recruiterId) {
        User recruiter = userRepository.findById(recruiterId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        if (!RECRUITER_ROLES.contains(recruiter.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RECRUITER_ROLE_REQUIRED", "Recruiter role required");
        }
        return recruiter;
    }

    private QuickChallenge findValidChallenge(String challengeToken, boolean forSubmission) {
        String token = challengeToken == null ? "" : challengeToken.trim();
        QuickChallenge challenge = quickChallengeRepository.findByChallengeToken(token)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUICK_CHALLENGE_NOT_FOUND", "Challenge not found"));

        LocalDateTime now = LocalDateTime.now();
        boolean outside24h = now.isAfter(challenge.getExpiresAt());
        boolean outsideCandidateWindow = challenge.getOpenedAt() != null
            && now.isAfter(challenge.getOpenedAt().plusSeconds(CANDIDATE_TIME_SECONDS));

        if ((outside24h || (forSubmission && outsideCandidateWindow))
                && challenge.getStatus() != QuickChallenge.QuickChallengeStatus.COMPLETED) {
            challenge.setStatus(QuickChallenge.QuickChallengeStatus.EXPIRED);
            quickChallengeRepository.save(challenge);
            throw new ApiException(HttpStatus.GONE, "QUICK_CHALLENGE_EXPIRED", "This challenge has expired");
        }

        return challenge;
    }

    private QuickChallenge findReusableChallenge(Long recruiterId, String badgeToken) {
        List<QuickChallenge> existing = quickChallengeRepository.findByBadgeTokenOrderByCreatedAtDesc(badgeToken);
        for (QuickChallenge challenge : existing) {
            if (!Objects.equals(challenge.getRecruiter().getId(), recruiterId)) {
                continue;
            }

            if (challenge.getStatus() == QuickChallenge.QuickChallengeStatus.COMPLETED
                    || challenge.getStatus() == QuickChallenge.QuickChallengeStatus.EXPIRED) {
                continue;
            }

            if (LocalDateTime.now().isAfter(challenge.getExpiresAt())) {
                challenge.setStatus(QuickChallenge.QuickChallengeStatus.EXPIRED);
                quickChallengeRepository.save(challenge);
                continue;
            }

            return challenge;
        }
        return null;
    }

    private int resolveTimeTakenSeconds(QuickChallenge challenge, Integer clientValue) {
        int fallback = CANDIDATE_TIME_SECONDS;
        if (challenge.getOpenedAt() != null) {
            long elapsed = Math.max(0, ChronoUnit.SECONDS.between(challenge.getOpenedAt(), LocalDateTime.now()));
            fallback = (int) Math.min(CANDIDATE_TIME_SECONDS, elapsed);
        }

        if (clientValue == null) {
            return fallback;
        }

        int boundedClient = Math.max(0, Math.min(clientValue, CANDIDATE_TIME_SECONDS));
        if (challenge.getOpenedAt() == null) {
            return boundedClient;
        }

        return Math.min(boundedClient, fallback);
    }

    private QuickChallengeResponse buildResponse(QuickChallenge challenge, boolean includeCodeAndQuestion, boolean includeAnswer) {
        long secondsRemaining = calculateSecondsRemaining(challenge);

        return QuickChallengeResponse.builder()
            .id(challenge.getId())
            .challengeToken(challenge.getChallengeToken())
            .badgeToken(challenge.getBadgeToken())
            .candidateUsername(challenge.getCandidateUsername())
            .repoName(challenge.getRepoName())
            .selectedFilePath(challenge.getSelectedFilePath())
            .status(challenge.getStatus().name())
            .overallScore(challenge.getOverallScore())
            .accuracyScore(challenge.getAccuracyScore())
            .depthScore(challenge.getDepthScore())
            .specificityScore(challenge.getSpecificityScore())
            .aiFeedback(challenge.getAiFeedback())
            .tabSwitchCount(challenge.getTabSwitchCount())
            .timeTakenSeconds(challenge.getTimeTakenSeconds())
            .createdAt(challenge.getCreatedAt())
            .openedAt(challenge.getOpenedAt())
            .expiresAt(challenge.getExpiresAt())
            .completedAt(challenge.getCompletedAt())
            .secondsRemaining(secondsRemaining)
            .candidateUrl("/quick-challenge/" + challenge.getChallengeToken())
            .codeSnippet(includeCodeAndQuestion ? challenge.getCodeSnippet() : null)
            .questionText(includeCodeAndQuestion ? challenge.getQuestionText() : null)
            .candidateAnswer(includeAnswer ? challenge.getCandidateAnswer() : null)
            .build();
    }

    private long calculateSecondsRemaining(QuickChallenge challenge) {
        if (challenge.getStatus() == QuickChallenge.QuickChallengeStatus.COMPLETED
                || challenge.getStatus() == QuickChallenge.QuickChallengeStatus.EXPIRED) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        long untilExpiry = Math.max(0, ChronoUnit.SECONDS.between(now, challenge.getExpiresAt()));
        if (challenge.getOpenedAt() == null) {
            return Math.min(CANDIDATE_TIME_SECONDS, untilExpiry);
        }

        long untilCandidateDeadline = Math.max(
            0,
            ChronoUnit.SECONDS.between(now, challenge.getOpenedAt().plusSeconds(CANDIDATE_TIME_SECONDS))
        );
        return Math.min(untilExpiry, untilCandidateDeadline);
    }

    private String extractFocusedSnippet(String codeContext) {
        if (codeContext == null || codeContext.isBlank()) {
            return "";
        }

        String[] lines = codeContext.split("\\r?\\n");
        int startLine = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.matches(".*\\b(public|private|protected|def|function|const|async|class)\\b.*")
                    && (line.contains("(") || line.contains("{"))) {
                startLine = Math.max(0, i - 2);
                break;
            }
        }

        int endLine = Math.min(startLine + 40, lines.length);
        List<String> snippetLines = new ArrayList<>();
        for (int i = startLine; i < endLine; i++) {
            snippetLines.add(lines[i]);
        }
        return String.join("\n", snippetLines).trim();
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            builder.append(TOKEN_CHARS.charAt(random.nextInt(TOKEN_CHARS.length())));
        }
        return builder.toString();
    }

    private int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String normalizeFileRef(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private List<SnippetSource> parseSummarySources(String codeSummary) {
        List<SnippetSource> sources = new ArrayList<>();
        if (codeSummary == null || codeSummary.isBlank()) {
            return sources;
        }

        Pattern sectionPattern = Pattern.compile(
            "--- FILE: (.+?) ---\\R(.*?)(?=\\R--- FILE: |\\z)",
            Pattern.DOTALL
        );
        Matcher matcher = sectionPattern.matcher(codeSummary);
        while (matcher.find()) {
            String filePath = safeText(matcher.group(1));
            String section = safeText(matcher.group(2));
            if (filePath.isBlank() || section.isBlank()) {
                continue;
            }

            String rawCode = section;
            int keySnippetIndex = section.indexOf("Key Code Snippet:");
            if (keySnippetIndex >= 0) {
                rawCode = section.substring(keySnippetIndex + "Key Code Snippet:".length()).trim();
            }

            String snippet = extractFocusedSnippet(rawCode);
            if (!snippet.isBlank() && snippet.length() >= 120) {
                sources.add(new SnippetSource(filePath, snippet));
            }
        }

        return sources;
    }

    private record SnippetSource(String filePath, String snippet) {}
}
