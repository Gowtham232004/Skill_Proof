package com.skillproof.backend_core.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.CreateLiveSessionRequest;
import com.skillproof.backend_core.dto.response.LiveAnswerSubmitResponse;
import com.skillproof.backend_core.dto.response.LiveQuestionRevealResponse;
import com.skillproof.backend_core.dto.response.LiveSessionResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.LiveSession;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.LiveSessionRepository;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveSessionService {

    private final LiveSessionRepository liveSessionRepository;
    private final BadgeRepository badgeRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper;

    private static final String SESSION_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SESSION_CODE_LENGTH = 6;

    private static final Set<User.Role> RECRUITER_ROLES = Set.of(
        User.Role.RECRUITER,
        User.Role.COMPANY,
        User.Role.ADMIN
    );

    public LiveSessionResponse createLiveSession(Long recruiterId, CreateLiveSessionRequest request) {
        User recruiter = ensureRecruiterRole(recruiterId);
        Badge badge = badgeRepository.findByVerificationToken(request.getBadgeToken())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "BADGE_NOT_FOUND",
                "Badge not found"
            ));

        List<LiveSession> existingSessions = liveSessionRepository.findByBadgeToken(request.getBadgeToken());
        LiveSession existingActive = existingSessions.stream()
            .filter(session ->
                session.getStatus() == LiveSession.LiveSessionStatus.PENDING
                    || session.getStatus() == LiveSession.LiveSessionStatus.ACTIVE
            )
            .findFirst()
            .orElse(null);
        if (existingActive != null) {
            if (!Objects.equals(existingActive.getRecruiter().getId(), recruiterId)) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LIVE_SESSION_OWNED_BY_ANOTHER_RECRUITER",
                    "An active live session already exists for this candidate under another recruiter account.",
                    Map.of(
                        "status", existingActive.getStatus().name()
                    )
                );
            }

            throw new ApiException(
                HttpStatus.CONFLICT,
                "LIVE_SESSION_ALREADY_EXISTS",
                "An active live session already exists for this candidate",
                Map.of(
                    "existingSessionCode", existingActive.getSessionCode(),
                    "status", existingActive.getStatus().name(),
                    "recruiterUrl", "/recruiter/live/" + existingActive.getSessionCode()
                )
            );
        }

        List<Question> questions = getSessionQuestions(badge.getSession().getId());
        if (questions.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "QUESTIONS_NOT_FOUND",
                "No verification questions found for this candidate"
            );
        }

        String sessionCode = generateUniqueSessionCode();

        LiveSession liveSession = LiveSession.builder()
            .sessionCode(sessionCode)
            .badgeToken(request.getBadgeToken())
            .recruiter(recruiter)
            .candidateEmail(request.getCandidateEmail())
            .candidateUsername(badge.getUser().getGithubUsername())
            .repoName(badge.getSession().getRepoName())
            .currentRevealedQuestion(0)
            .liveAnswersJson("[]")
            .status(LiveSession.LiveSessionStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        LiveSession saved = liveSessionRepository.save(liveSession);
        log.info("Live session created: code={} badge={} recruiter={}", sessionCode, request.getBadgeToken(), recruiterId);

        return buildLiveSessionResponse(saved, questions.size());
    }

    public LiveQuestionRevealResponse revealNextQuestion(String sessionCode, Long recruiterId) {
        LiveSession session = findActiveSession(sessionCode);

        if (!Objects.equals(session.getRecruiter().getId(), recruiterId)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "LIVE_SESSION_FORBIDDEN",
                "You do not own this live session"
            );
        }

        List<Question> questions = getQuestionsForBadgeToken(session.getBadgeToken());
        int nextQuestion = session.getCurrentRevealedQuestion() + 1;
        if (nextQuestion > questions.size()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ALL_QUESTIONS_REVEALED",
                "All questions have already been revealed"
            );
        }

        session.setCurrentRevealedQuestion(nextQuestion);
        session.setStatus(LiveSession.LiveSessionStatus.ACTIVE);
        liveSessionRepository.save(session);

        Question question = questions.get(nextQuestion - 1);
        return LiveQuestionRevealResponse.builder()
            .sessionCode(sessionCode)
            .questionNumber(nextQuestion)
            .totalQuestions(questions.size())
            .questionText(question.getQuestionText())
            .difficulty(question.getDifficulty().name())
            .fileReference(question.getFileReference())
            .codeContext(question.getCodeContext())
            .isLastQuestion(nextQuestion == questions.size())
            .build();
    }

    public LiveAnswerSubmitResponse submitLiveAnswer(String sessionCode, Integer questionNumber, String answerText) {
        LiveSession session = findActiveSession(sessionCode);

        if (!Objects.equals(questionNumber, session.getCurrentRevealedQuestion())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "LIVE_QUESTION_NOT_ACTIVE",
                "Question " + questionNumber + " is not currently active"
            );
        }

        String trimmedAnswer = answerText == null ? "" : answerText.trim();
        if (trimmedAnswer.length() < 20) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "LIVE_ANSWER_TOO_SHORT",
                "Answer must be at least 20 characters"
            );
        }

        List<Question> questions = getQuestionsForBadgeToken(session.getBadgeToken());
        Question question = questions.get(questionNumber - 1);

        List<Map<String, Object>> answers = readLiveAnswers(session.getLiveAnswersJson());
        boolean duplicate = answers.stream().anyMatch(item ->
            Objects.equals(toInt(item.get("questionNumber")), questionNumber)
        );
        if (duplicate) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "LIVE_ANSWER_ALREADY_SUBMITTED",
                "Answer already submitted for this question"
            );
        }

        Map<String, Object> evalResult = aiGatewayService.evaluateSingleAnswer(
            question.getQuestionText(),
            question.getFileReference(),
            question.getCodeContext(),
            trimmedAnswer
        );

        int accuracy = toInt(evalResult.get("accuracy_score"));
        int depth = toInt(evalResult.get("depth_score"));
        int specificity = toInt(evalResult.get("specificity_score"));
        String feedback = String.valueOf(evalResult.getOrDefault("ai_feedback", "Evaluation unavailable"));
        int composite = ((accuracy * 4 + depth * 3 + specificity * 3) / 10) * 10;

        Map<String, Object> answerRecord = new HashMap<>();
        answerRecord.put("questionNumber", questionNumber);
        answerRecord.put("answerText", trimmedAnswer);
        answerRecord.put("accuracyScore", accuracy);
        answerRecord.put("depthScore", depth);
        answerRecord.put("specificityScore", specificity);
        answerRecord.put("compositeScore", composite);
        answerRecord.put("aiFeedback", feedback);
        answerRecord.put("questionText", question.getQuestionText());
        answerRecord.put("fileReference", question.getFileReference());
        answerRecord.put("codeContext", question.getCodeContext());
        answers.add(answerRecord);

        session.setLiveAnswersJson(writeLiveAnswers(answers));

        boolean allQuestionsAnswered = answers.size() == questions.size();
        Integer overallScore = null;
        if (allQuestionsAnswered) {
            int total = answers.stream().mapToInt(item -> toInt(item.get("compositeScore"))).sum();
            overallScore = answers.isEmpty() ? 0 : total / answers.size();
            session.setLiveScore(overallScore);
            session.setStatus(LiveSession.LiveSessionStatus.COMPLETED);
            session.setCompletedAt(LocalDateTime.now());
            log.info("Live session {} completed with score {}", sessionCode, overallScore);
        }

        liveSessionRepository.save(session);

        return LiveAnswerSubmitResponse.builder()
            .sessionCode(sessionCode)
            .questionNumber(questionNumber)
            .accuracyScore(accuracy)
            .depthScore(depth)
            .specificityScore(specificity)
            .compositeScore(composite)
            .aiFeedback(feedback)
            .allQuestionsAnswered(allQuestionsAnswered)
            .overallLiveScore(overallScore)
            .build();
    }

    public LiveSessionResponse getSessionStatus(String sessionCode) {
        LiveSession session = liveSessionRepository.findBySessionCode(sessionCode)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "LIVE_SESSION_NOT_FOUND",
                "Live session not found"
            ));

        if (LocalDateTime.now().isAfter(session.getExpiresAt())
            && session.getStatus() != LiveSession.LiveSessionStatus.COMPLETED
            && session.getStatus() != LiveSession.LiveSessionStatus.EXPIRED) {
            session.setStatus(LiveSession.LiveSessionStatus.EXPIRED);
            liveSessionRepository.save(session);
        }

        int totalQuestions = getQuestionsForBadgeToken(session.getBadgeToken()).size();
        return buildLiveSessionResponse(session, totalQuestions);
    }

    public List<Map<String, Object>> getLiveSessionAnswers(String sessionCode, Long recruiterId) {
        LiveSession session = liveSessionRepository.findBySessionCode(sessionCode)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "LIVE_SESSION_NOT_FOUND",
                "Live session not found"
            ));

        if (!Objects.equals(session.getRecruiter().getId(), recruiterId)) {
            log.warn(
                "Live answers access denied: sessionCode={} requestedBy={} owner={}",
                sessionCode,
                recruiterId,
                session.getRecruiter().getId()
            );
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "LIVE_SESSION_FORBIDDEN",
                "Access denied"
            );
        }

        List<Map<String, Object>> answers = readLiveAnswers(session.getLiveAnswersJson());
        log.info(
            "Live answers fetched: sessionCode={} recruiterId={} answersCount={}",
            sessionCode,
            recruiterId,
            answers.size()
        );
        return answers;
    }

    public Map<String, Object> getCandidateQuestion(String sessionCode, Integer questionNumber) {
        LiveSession session = liveSessionRepository.findBySessionCode(sessionCode)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "LIVE_SESSION_NOT_FOUND",
                "Live session not found"
            ));

        if (questionNumber > session.getCurrentRevealedQuestion()) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "LIVE_QUESTION_NOT_REVEALED",
                "This question has not been revealed yet"
            );
        }

        List<Question> questions = getQuestionsForBadgeToken(session.getBadgeToken());
        if (questionNumber < 1 || questionNumber > questions.size()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "LIVE_QUESTION_INVALID",
                "Invalid question number"
            );
        }

        Question question = questions.get(questionNumber - 1);
        Map<String, Object> payload = new HashMap<>();
        payload.put("questionNumber", questionNumber);
        payload.put("questionText", question.getQuestionText());
        payload.put("difficulty", question.getDifficulty().name());
        payload.put("fileReference", question.getFileReference());
        return payload;
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

    private LiveSession findActiveSession(String sessionCode) {
        LiveSession session = liveSessionRepository.findBySessionCode(sessionCode)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "LIVE_SESSION_NOT_FOUND",
                "Live session not found"
            ));

        if (session.getStatus() == LiveSession.LiveSessionStatus.COMPLETED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "LIVE_SESSION_COMPLETED",
                "This live session is already completed"
            );
        }

        if (session.getStatus() == LiveSession.LiveSessionStatus.EXPIRED
            || LocalDateTime.now().isAfter(session.getExpiresAt())) {
            if (session.getStatus() != LiveSession.LiveSessionStatus.EXPIRED) {
                session.setStatus(LiveSession.LiveSessionStatus.EXPIRED);
                liveSessionRepository.save(session);
            }
            throw new ApiException(
                HttpStatus.GONE,
                "LIVE_SESSION_EXPIRED",
                "This live session has expired"
            );
        }
        return session;
    }

    private List<Question> getQuestionsForBadgeToken(String badgeToken) {
        Badge badge = badgeRepository.findByVerificationToken(badgeToken)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "BADGE_NOT_FOUND",
                "Badge not found"
            ));
        return getSessionQuestions(badge.getSession().getId());
    }

    private List<Question> getSessionQuestions(Long sessionId) {
        return questionRepository.findBySessionIdOrderByQuestionNumber(sessionId);
    }

    private String generateUniqueSessionCode() {
        SecureRandom random = new SecureRandom();
        String code;
        int attempts = 0;
        do {
            StringBuilder sb = new StringBuilder(SESSION_CODE_LENGTH);
            for (int i = 0; i < SESSION_CODE_LENGTH; i++) {
                sb.append(SESSION_CODE_CHARS.charAt(random.nextInt(SESSION_CODE_CHARS.length())));
            }
            code = sb.toString();
            attempts++;
            if (attempts > 100) {
                throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "LIVE_SESSION_CODE_GENERATION_FAILED",
                    "Could not generate unique session code"
                );
            }
        } while (liveSessionRepository.findBySessionCode(code).isPresent());
        return code;
    }

    private LiveSessionResponse buildLiveSessionResponse(LiveSession session, int totalQuestions) {
        return LiveSessionResponse.builder()
            .id(session.getId())
            .sessionCode(session.getSessionCode())
            .badgeToken(session.getBadgeToken())
            .candidateUsername(session.getCandidateUsername())
            .repoName(session.getRepoName())
            .currentRevealedQuestion(session.getCurrentRevealedQuestion())
            .status(session.getStatus().name())
            .liveScore(session.getLiveScore())
            .createdAt(session.getCreatedAt())
            .expiresAt(session.getExpiresAt())
            .recruiterUrl("/recruiter/live/" + session.getSessionCode())
            .candidateUrl("/verify/live/" + session.getSessionCode())
            .totalQuestions(totalQuestions)
            .build();
    }

    private List<Map<String, Object>> readLiveAnswers(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse live answers JSON", ex);
            return new ArrayList<>();
        }
    }

    private String writeLiveAnswers(List<Map<String, Object>> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "LIVE_ANSWER_SERIALIZATION_FAILED",
                "Could not persist live answers"
            );
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
