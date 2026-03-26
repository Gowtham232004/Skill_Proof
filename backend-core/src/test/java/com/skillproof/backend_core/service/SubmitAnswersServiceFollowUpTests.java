package com.skillproof.backend_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.SubmitFollowUpAnswersRequest;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.AnswerRepository;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.VerificationSessionRepository;

@ExtendWith(MockitoExtension.class)
class SubmitAnswersServiceFollowUpTests {

    @Mock
    private VerificationSessionRepository sessionRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private AiGatewayService aiGatewayService;

    @Mock
    private BadgeService badgeService;

    @Mock
    private GapAnalyzerService gapAnalyzerService;

    @Test
    void submitFollowUpAnswersRejectsPromptMismatch() {
        SubmitAnswersService service = new SubmitAnswersService(
            sessionRepository,
            questionRepository,
            answerRepository,
            badgeRepository,
            new ObjectMapper(),
            aiGatewayService,
            badgeService,
            gapAnalyzerService
        );

        User user = User.builder().id(99L).githubUsername("dev").build();
        VerificationSession session = VerificationSession.builder()
            .id(500L)
            .user(user)
            .repoLanguage("Java")
            .build();

        Question question = Question.builder()
            .id(10L)
            .session(session)
            .questionNumber(1)
            .difficulty(Question.Difficulty.MEDIUM)
            .questionType(Question.QuestionType.CODE_GROUNDED)
            .fileReference("Service.java")
            .questionText("Original")
            .build();

        Badge badge = Badge.builder()
            .id(77L)
            .session(session)
            .user(user)
            .verificationToken("tok")
            .followUpPromptsJson("[{\"questionNumber\":1,\"followUpQuestion\":\"Issued follow-up question\"}]")
            .build();

        SubmitFollowUpAnswersRequest request = new SubmitFollowUpAnswersRequest();
        request.setSessionId(500L);

        SubmitFollowUpAnswersRequest.FollowUpAnswerItem item = new SubmitFollowUpAnswersRequest.FollowUpAnswerItem();
        item.setQuestionNumber(1);
        item.setFollowUpQuestion("Tampered follow-up question");
        item.setAnswerText("This answer is sufficiently long for validation.");
        item.setSkipped(false);
        request.setFollowUps(List.of(item));

        when(sessionRepository.findById(500L)).thenReturn(Optional.of(session));
        when(badgeRepository.findBySessionId(500L)).thenReturn(Optional.of(badge));
        when(questionRepository.findBySessionOrderByQuestionNumber(session)).thenReturn(List.of(question));

        ApiException ex = assertThrows(ApiException.class,
            () -> service.submitFollowUpAnswers(99L, request));

        assertEquals("FOLLOW_UP_PROMPT_MISMATCH", ex.getCode());
    }

    @Test
    void submitFollowUpAnswersRejectsDuplicateQuestionEntries() {
        SubmitAnswersService service = new SubmitAnswersService(
            sessionRepository,
            questionRepository,
            answerRepository,
            badgeRepository,
            new ObjectMapper(),
            aiGatewayService,
            badgeService,
            gapAnalyzerService
        );

        User user = User.builder().id(100L).githubUsername("dev2").build();
        VerificationSession session = VerificationSession.builder()
            .id(600L)
            .user(user)
            .repoLanguage("Java")
            .build();

        Question question = Question.builder()
            .id(11L)
            .session(session)
            .questionNumber(2)
            .difficulty(Question.Difficulty.HARD)
            .questionType(Question.QuestionType.CODE_GROUNDED)
            .fileReference("Api.java")
            .questionText("Original")
            .build();

        Badge badge = Badge.builder()
            .id(88L)
            .session(session)
            .user(user)
            .verificationToken("tok2")
            .followUpPromptsJson("[]")
            .build();

        SubmitFollowUpAnswersRequest request = new SubmitFollowUpAnswersRequest();
        request.setSessionId(600L);

        SubmitFollowUpAnswersRequest.FollowUpAnswerItem a = new SubmitFollowUpAnswersRequest.FollowUpAnswerItem();
        a.setQuestionNumber(2);
        a.setFollowUpQuestion("Q one");
        a.setAnswerText("This answer is long enough for follow-up one.");
        a.setSkipped(false);

        SubmitFollowUpAnswersRequest.FollowUpAnswerItem b = new SubmitFollowUpAnswersRequest.FollowUpAnswerItem();
        b.setQuestionNumber(2);
        b.setFollowUpQuestion("Q two");
        b.setAnswerText("This answer is long enough for follow-up two.");
        b.setSkipped(false);

        request.setFollowUps(List.of(a, b));

        when(sessionRepository.findById(600L)).thenReturn(Optional.of(session));
        when(badgeRepository.findBySessionId(600L)).thenReturn(Optional.of(badge));
        when(questionRepository.findBySessionOrderByQuestionNumber(session)).thenReturn(List.of(question));

        ApiException ex = assertThrows(ApiException.class,
            () -> service.submitFollowUpAnswers(100L, request));

        assertEquals("FOLLOW_UP_DUPLICATE_QUESTION", ex.getCode());
    }
}
