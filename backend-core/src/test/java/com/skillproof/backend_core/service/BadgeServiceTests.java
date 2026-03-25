package com.skillproof.backend_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
    private HmacUtil hmacUtil;

    @Test
    void getBadgeByTokenReturnsConfidenceAndIntegritySignals() {
        BadgeService badgeService = new BadgeService(
            badgeRepository,
            sessionRepository,
            questionRepository,
            answerRepository,
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
    }

    @Test
    void getBadgeByTokenReturnsInvalidWhenBadgeMissing() {
        BadgeService badgeService = new BadgeService(
            badgeRepository,
            sessionRepository,
            questionRepository,
            answerRepository,
            hmacUtil,
            new ObjectMapper()
        );

        when(badgeRepository.findByVerificationToken("missing")).thenReturn(Optional.empty());

        BadgeResponse response = badgeService.getBadgeByToken("missing");

        assertFalse(response.isValid());
        assertEquals("missing", response.getVerificationToken());
    }

    @Test
    void getBadgeByTokenFallsBackToEmptyFrameworksOnInvalidJson() {
        BadgeService badgeService = new BadgeService(
            badgeRepository,
            sessionRepository,
            questionRepository,
            answerRepository,
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

        BadgeResponse response = badgeService.getBadgeByToken("bad_frameworks");

        assertTrue(response.isValid());
        assertTrue(response.getFrameworksDetected().isEmpty());
    }
}
