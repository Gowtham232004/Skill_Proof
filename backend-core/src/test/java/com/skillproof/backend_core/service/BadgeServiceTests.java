package com.skillproof.backend_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.model.Answer;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.AnswerRepository;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.ChallengeSubmissionRepository;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.VerificationSessionRepository;
import com.skillproof.backend_core.util.HmacUtil;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTests {

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private VerificationSessionRepository sessionRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private ChallengeSubmissionRepository challengeSubmissionRepository;

    @Mock
    private HmacUtil hmacUtil;

    @Test
    void getBadgeByTokenReturnsConfidenceAndIntegritySignals() {
        BadgeService badgeService = new BadgeService(
            badgeRepository,
            sessionRepository,
            questionRepository,
            answerRepository,
            challengeSubmissionRepository,
            hmacUtil,
            new ObjectMapper()
        );

        User user = User.builder()
            .id(7L)
            .githubUsername("dev-user")
            .avatarUrl("https://example.com/avatar.png")
            .displayName("Dev User")
            .build();

        VerificationSession session = VerificationSession.builder()
            .id(10L)
            .user(user)
            .repoOwner("octocat")
            .repoName("skillproof")
            .repoDescription("Verification platform")
            .repoLanguage("Java")
            .frameworksDetected("[\"Spring Boot\",\"JWT\"]")
            .status(VerificationSession.Status.COMPLETED)
            .build();

        Badge badge = Badge.builder()
            .id(11L)
            .session(session)
            .user(user)
            .verificationToken("sp_token")
            .overallScore(88)
            .backendScore(90)
            .apiDesignScore(85)
            .errorHandlingScore(84)
            .codeQualityScore(89)
            .documentationScore(82)
            .confidenceTier("High")
            .tabSwitches(1)
            .pasteCount(0)
            .avgAnswerSeconds(42)
            .isActive(true)
            .issuedAt(LocalDateTime.now())
            .build();

        when(badgeRepository.findByVerificationToken("sp_token")).thenReturn(Optional.of(badge));
        when(sessionRepository.countByUserAndRepoOwnerAndRepoNameAndStatus(
            user,
            "octocat",
            "skillproof",
            VerificationSession.Status.COMPLETED
        )).thenReturn(2L);
        when(questionRepository.countBySessionId(10L)).thenReturn(5L);

        Question question = Question.builder()
            .id(100L)
            .session(session)
            .questionNumber(1)
            .difficulty(Question.Difficulty.EASY)
            .questionType(Question.QuestionType.CODE_GROUNDED)
            .fileReference("AuthService.java")
            .questionText("Explain auth flow")
            .build();

        Answer answer = Answer.builder()
            .id(1000L)
            .question(question)
            .answerText("The flow starts in AuthController and uses jwtUtil for token creation.")
            .accuracyScore(8)
            .depthScore(7)
            .specificityScore(8)
            .compositeScore(7.7)
            .aiFeedback("Good reference to controller and token generation. Could explain failure path.")
            .build();

        when(answerRepository.findByQuestionSessionIdOrderByQuestionQuestionNumberAsc(10L))
            .thenReturn(java.util.List.of(answer));
        when(challengeSubmissionRepository.findByCandidateIdOrderByCreatedAtDesc(anyLong()))
            .thenReturn(List.of());

        BadgeResponse response = badgeService.getBadgeByToken("sp_token");

        assertTrue(response.isValid());
        assertEquals("High", response.getConfidenceTier());
        assertEquals(1, response.getTabSwitches());
        assertEquals(0, response.getPasteCount());
        assertEquals(42, response.getAvgAnswerSeconds());
        assertEquals(2, response.getRepoAttemptCount());
        assertEquals(1, response.getAnsweredCount());
        assertEquals(5, response.getTotalQuestions());
        assertTrue(response.getEvaluationComplete());
        assertEquals(1, response.getQuestionResults().size());
        assertEquals(2, response.getFrameworksDetected().size());
        assertEquals("Spring Boot", response.getFrameworksDetected().get(0));
        assertEquals(1, response.getScoreByQuestionType().size());
        assertEquals(70, response.getScoreByQuestionType().get("CODE_GROUNDED"));
    }

    @Test
    void getBadgeByTokenComputesScoreByQuestionTypeForMixedQuestions() {
        BadgeService badgeService = new BadgeService(
            badgeRepository,
            sessionRepository,
            questionRepository,
            answerRepository,
            challengeSubmissionRepository,
            hmacUtil,
            new ObjectMapper()
        );

        User user = User.builder()
            .id(19L)
            .githubUsername("mixed-dev")
            .build();

        VerificationSession session = VerificationSession.builder()
            .id(101L)
            .user(user)
            .repoOwner("octo")
            .repoName("mixed-repo")
            .frameworksDetected("[]")
            .status(VerificationSession.Status.COMPLETED)
            .build();

        Badge badge = Badge.builder()
            .session(session)
            .user(user)
            .verificationToken("mixed_token")
            .overallScore(80)
            .isActive(true)
            .build();

        when(badgeRepository.findByVerificationToken("mixed_token")).thenReturn(Optional.of(badge));
        when(sessionRepository.countByUserAndRepoOwnerAndRepoNameAndStatus(
            user,
            "octo",
            "mixed-repo",
            VerificationSession.Status.COMPLETED
        )).thenReturn(1L);
        when(questionRepository.countBySessionId(101L)).thenReturn(2L);

        Question codeQuestion = Question.builder()
            .id(501L)
            .session(session)
            .questionNumber(1)
            .difficulty(Question.Difficulty.MEDIUM)
            .questionType(Question.QuestionType.CODE_GROUNDED)
            .fileReference("Service.java")
            .questionText("Code grounded")
            .build();

        Question conceptualQuestion = Question.builder()
            .id(502L)
            .session(session)
            .questionNumber(2)
            .difficulty(Question.Difficulty.HARD)
            .questionType(Question.QuestionType.CONCEPTUAL)
            .fileReference("")
            .questionText("Conceptual")
            .build();

        Answer codeAnswer = Answer.builder()
            .id(6001L)
            .question(codeQuestion)
            .answerText("code")
            .accuracyScore(8)
            .depthScore(8)
            .specificityScore(8)
            .compositeScore(8.0)
            .aiFeedback("good")
            .build();

        Answer conceptualAnswer = Answer.builder()
            .id(6002L)
            .question(conceptualQuestion)
            .answerText("concept")
            .accuracyScore(5)
            .depthScore(5)
            .specificityScore(5)
            .compositeScore(5.0)
            .aiFeedback("ok")
            .build();

        when(answerRepository.findByQuestionSessionIdOrderByQuestionQuestionNumberAsc(101L))
            .thenReturn(List.of(codeAnswer, conceptualAnswer));
        when(challengeSubmissionRepository.findByCandidateIdOrderByCreatedAtDesc(anyLong()))
            .thenReturn(List.of());

        BadgeResponse response = badgeService.getBadgeByToken("mixed_token");

        assertTrue(response.isValid());
        assertEquals(80, response.getScoreByQuestionType().get("CODE_GROUNDED"));
        assertEquals(50, response.getScoreByQuestionType().get("CONCEPTUAL"));
    }

    @Test
    void getBadgeByTokenReturnsInvalidWhenBadgeMissing() {
        BadgeService badgeService = new BadgeService(
            badgeRepository,
            sessionRepository,
            questionRepository,
            answerRepository,
            challengeSubmissionRepository,
            hmacUtil,
            new ObjectMapper()
        );
        when(challengeSubmissionRepository.findByCandidateIdOrderByCreatedAtDesc(anyLong()))
            .thenReturn(List.of());

        when(badgeRepository.findByVerificationToken("missing")).thenReturn(Optional.empty());

        BadgeResponse response = badgeService.getBadgeByToken("missing");

        assertFalse(response.isValid());
        assertEquals("missing", response.getVerificationToken());
    }

    @Test
    void getBadgeByTokenParsesFollowUpResults() {
        BadgeService badgeService = new BadgeService(
            badgeRepository,
            sessionRepository,
            questionRepository,
            answerRepository,
            challengeSubmissionRepository,
            hmacUtil,
            new ObjectMapper()
        );

        User user = User.builder().id(23L).githubUsername("followup-dev").build();
        VerificationSession session = VerificationSession.builder()
            .id(230L)
            .user(user)
            .repoOwner("owner")
            .repoName("repo")
            .frameworksDetected("[]")
            .status(VerificationSession.Status.COMPLETED)
            .build();

        Badge badge = Badge.builder()
            .session(session)
            .user(user)
            .verificationToken("followup_token")
            .isActive(true)
            .followUpRequiredCount(2)
            .followUpAnsweredCount(1)
            .followUpResultsJson("[{\"questionNumber\":1,\"followUpQuestion\":\"Explain cache invalidation\",\"skipped\":false,\"answerLength\":124,\"answerExcerpt\":\"I invalidate on write events\"}]")
            .build();

        when(badgeRepository.findByVerificationToken("followup_token")).thenReturn(Optional.of(badge));
        when(sessionRepository.countByUserAndRepoOwnerAndRepoNameAndStatus(
            user,
            "owner",
            "repo",
            VerificationSession.Status.COMPLETED
        )).thenReturn(1L);
        when(questionRepository.countBySessionId(230L)).thenReturn(0L);
        when(answerRepository.findByQuestionSessionIdOrderByQuestionQuestionNumberAsc(230L))
            .thenReturn(List.of());
        when(challengeSubmissionRepository.findByCandidateIdOrderByCreatedAtDesc(anyLong()))
            .thenReturn(List.of());

        BadgeResponse response = badgeService.getBadgeByToken("followup_token");

        assertTrue(response.isValid());
        assertEquals(2, response.getFollowUpRequiredCount());
        assertEquals(1, response.getFollowUpAnsweredCount());
        assertEquals(1, response.getFollowUpResults().size());
        assertEquals(1, response.getFollowUpResults().get(0).getQuestionNumber());
        assertEquals(false, response.getFollowUpResults().get(0).getSkipped());
    }

    @Test
    void getBadgeByTokenFallsBackToEmptyFrameworksOnInvalidJson() {
        BadgeService badgeService = new BadgeService(
            badgeRepository,
            sessionRepository,
            questionRepository,
            answerRepository,
            challengeSubmissionRepository,
            hmacUtil,
            new ObjectMapper()
        );

        User user = User.builder().id(3L).githubUsername("dev").build();

        VerificationSession session = VerificationSession.builder()
            .id(9L)
            .user(user)
            .repoOwner("owner")
            .repoName("repo")
            .frameworksDetected("not-json")
            .status(VerificationSession.Status.COMPLETED)
            .build();

        Badge badge = Badge.builder()
            .session(session)
            .user(user)
            .verificationToken("bad_frameworks")
            .isActive(true)
            .build();

        when(badgeRepository.findByVerificationToken("bad_frameworks")).thenReturn(Optional.of(badge));
        when(sessionRepository.countByUserAndRepoOwnerAndRepoNameAndStatus(
            user,
            "owner",
            "repo",
            VerificationSession.Status.COMPLETED
        )).thenReturn(1L);
        when(questionRepository.countBySessionId(9L)).thenReturn(0L);
        when(answerRepository.findByQuestionSessionIdOrderByQuestionQuestionNumberAsc(9L))
            .thenReturn(java.util.List.of());
        when(challengeSubmissionRepository.findByCandidateIdOrderByCreatedAtDesc(anyLong()))
            .thenReturn(List.of());

        BadgeResponse response = badgeService.getBadgeByToken("bad_frameworks");

        assertTrue(response.isValid());
        assertTrue(response.getFrameworksDetected().isEmpty());
    }
}
