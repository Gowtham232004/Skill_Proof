package com.skillproof.backend_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.StartVerificationRequest;
import com.skillproof.backend_core.dto.response.VerificationStartResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.UserRepository;
import com.skillproof.backend_core.repository.VerificationSessionRepository;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTests {

    @Mock
    private GitHubService gitHubService;

    @Mock
    private FileFilterService fileFilterService;

    @Mock
    private CodeExtractorService codeExtractorService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationSessionRepository sessionRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AiGatewayService aiGatewayService;

    @Test
    void startVerificationBlocksWithinCooldownWindow() {
        VerificationService verificationService = new VerificationService(
            gitHubService,
            fileFilterService,
            codeExtractorService,
            userRepository,
            sessionRepository,
            questionRepository,
            new ObjectMapper(),
            aiGatewayService
        );

        User user = User.builder()
            .id(101L)
            .githubUserId("101")
            .githubUsername("dev")
            .plan(User.Plan.FREE)
            .build();

        StartVerificationRequest request = new StartVerificationRequest();
        request.setRepoOwner("Gowtham232004");
        request.setRepoName("AutoML");

        VerificationSession lastCompleted = VerificationSession.builder()
            .id(500L)
            .user(user)
            .repoOwner("gowtham232004")
            .repoName("automl")
            .status(VerificationSession.Status.COMPLETED)
            .startedAt(LocalDateTime.now().minusHours(2))
            .build();

        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(sessionRepository.countByUserAndStatus(user, VerificationSession.Status.COMPLETED)).thenReturn(0L);
        when(sessionRepository.findTopByUserAndRepoOwnerIgnoreCaseAndRepoNameIgnoreCaseAndStatusOrderByStartedAtDesc(
            user,
            "Gowtham232004",
            "AutoML",
            VerificationSession.Status.COMPLETED
        )).thenReturn(Optional.of(lastCompleted));

        ApiException ex = assertThrows(ApiException.class,
            () -> verificationService.startVerification(101L, request));

        assertEquals("COOLDOWN_ACTIVE", ex.getCode());
    }

    @Test
    void startVerificationBypassModeSkipsCooldownCheck() {
        VerificationService verificationService = new VerificationService(
            gitHubService,
            fileFilterService,
            codeExtractorService,
            userRepository,
            sessionRepository,
            questionRepository,
            new ObjectMapper(),
            aiGatewayService
        );

        ReflectionTestUtils.setField(verificationService, "bypassVerificationLimit", true);

        User user = User.builder()
            .id(111L)
            .githubUserId("111")
            .githubUsername("dev-bypass")
            .plan(User.Plan.FREE)
            .build();

        StartVerificationRequest request = new StartVerificationRequest();
        request.setRepoOwner("Gowtham232004");
        request.setRepoName("AutoML");

        when(userRepository.findById(111L)).thenReturn(Optional.of(user));

        assertThrows(NullPointerException.class,
            () -> verificationService.startVerification(111L, request));

        verify(sessionRepository, never())
            .findTopByUserAndRepoOwnerIgnoreCaseAndRepoNameIgnoreCaseAndStatusOrderByStartedAtDesc(
                user,
                "Gowtham232004",
                "AutoML",
                VerificationSession.Status.COMPLETED
            );
    }

    @Test
    void startVerificationUsesCompletedAtWhenStartedAtMissing() {
        VerificationService verificationService = new VerificationService(
            gitHubService,
            fileFilterService,
            codeExtractorService,
            userRepository,
            sessionRepository,
            questionRepository,
            new ObjectMapper(),
            aiGatewayService
        );

        User user = User.builder()
            .id(202L)
            .githubUserId("202")
            .githubUsername("dev2")
            .plan(User.Plan.FREE)
            .build();

        StartVerificationRequest request = new StartVerificationRequest();
        request.setRepoOwner("OrgName");
        request.setRepoName("RepoName");

        VerificationSession lastCompleted = VerificationSession.builder()
            .id(600L)
            .user(user)
            .repoOwner("orgname")
            .repoName("reponame")
            .status(VerificationSession.Status.COMPLETED)
            .startedAt(null)
            .completedAt(LocalDateTime.now().minusHours(3))
            .build();

        when(userRepository.findById(202L)).thenReturn(Optional.of(user));
        when(sessionRepository.countByUserAndStatus(user, VerificationSession.Status.COMPLETED)).thenReturn(0L);
        when(sessionRepository.findTopByUserAndRepoOwnerIgnoreCaseAndRepoNameIgnoreCaseAndStatusOrderByStartedAtDesc(
            user,
            "OrgName",
            "RepoName",
            VerificationSession.Status.COMPLETED
        )).thenReturn(Optional.of(lastCompleted));

        ApiException ex = assertThrows(ApiException.class,
            () -> verificationService.startVerification(202L, request));

        assertEquals("COOLDOWN_ACTIVE", ex.getCode());
    }

    @Test
    void startVerificationInMixedModePassesConfiguredCountsToAiGateway() {
        VerificationService verificationService = new VerificationService(
            gitHubService,
            fileFilterService,
            codeExtractorService,
            userRepository,
            sessionRepository,
            questionRepository,
            new ObjectMapper(),
            aiGatewayService
        );

        ReflectionTestUtils.setField(verificationService, "bypassVerificationLimit", true);
        ReflectionTestUtils.setField(verificationService, "mixedQuestionsEnabled", true);
        ReflectionTestUtils.setField(verificationService, "phase2TotalQuestions", 7);
        ReflectionTestUtils.setField(verificationService, "phase2ConceptualQuestions", 3);

        User user = User.builder()
            .id(303L)
            .githubUserId("303")
            .githubUsername("phase2-dev")
            .githubAccessToken("token")
            .plan(User.Plan.FREE)
            .build();

        StartVerificationRequest request = new StartVerificationRequest();
        request.setRepoOwner("Org");
        request.setRepoName("Repo");

        Map<String, String> fileContents = Map.of(
            "src/A.java", "class A {}",
            "src/B.java", "class B {}"
        );
        List<Question> generatedQuestions = List.of(
            buildQuestion(1, Question.Difficulty.EASY, Question.QuestionType.CODE_GROUNDED, "src/A.java"),
            buildQuestion(2, Question.Difficulty.EASY, Question.QuestionType.CODE_GROUNDED, "src/B.java"),
            buildQuestion(3, Question.Difficulty.MEDIUM, Question.QuestionType.CODE_GROUNDED, "src/A.java"),
            buildQuestion(4, Question.Difficulty.MEDIUM, Question.QuestionType.CODE_GROUNDED, "src/B.java"),
            buildQuestion(5, Question.Difficulty.MEDIUM, Question.QuestionType.CONCEPTUAL, ""),
            buildQuestion(6, Question.Difficulty.HARD, Question.QuestionType.CONCEPTUAL, ""),
            buildQuestion(7, Question.Difficulty.HARD, Question.QuestionType.CONCEPTUAL, "")
        );

        when(userRepository.findById(303L)).thenReturn(Optional.of(user));
        when(gitHubService.getRepositoryInfo("token", "Org", "Repo"))
            .thenReturn(Map.of("description", "desc", "language", "Java"));
        when(gitHubService.getRepositoryFileTree("token", "Org", "Repo"))
            .thenReturn(List.of("src/A.java", "src/B.java", "README.md"));
        when(fileFilterService.filterAndRankFiles(List.of("src/A.java", "src/B.java", "README.md")))
            .thenReturn(List.of("src/A.java", "src/B.java"));
        when(gitHubService.fetchFileContents("token", "Org", "Repo", List.of("src/A.java", "src/B.java")))
            .thenReturn(fileContents);
        when(fileFilterService.detectFrameworks(anyList(), eq(fileContents)))
            .thenReturn(List.of("Spring Boot"));
        when(fileFilterService.detectPrimaryLanguage(List.of("src/A.java", "src/B.java", "README.md")))
            .thenReturn("Java");
        when(codeExtractorService.extractCodeSummary(fileContents, "Java"))
            .thenReturn("summary");
        when(sessionRepository.save(any(VerificationSession.class))).thenAnswer(invocation -> {
            VerificationSession session = invocation.getArgument(0);
            session.setId(700L);
            return session;
        });
        when(aiGatewayService.generateQuestionsViaAI(
            any(VerificationSession.class),
            eq("summary"),
            eq("Java"),
            eq(List.of("Spring Boot")),
            eq(fileContents),
            eq(7),
            eq(3)
        )).thenReturn(generatedQuestions);
        when(questionRepository.saveAll(generatedQuestions)).thenReturn(generatedQuestions);

        VerificationStartResponse response = verificationService.startVerification(303L, request);

        assertEquals(7, response.getQuestions().size());
        long conceptualCount = response.getQuestions().stream()
            .filter(q -> "CONCEPTUAL".equals(q.getQuestionType()))
            .count();
        assertEquals(3, conceptualCount);

        verify(aiGatewayService).generateQuestionsViaAI(
            any(VerificationSession.class),
            eq("summary"),
            eq("Java"),
            eq(List.of("Spring Boot")),
            eq(fileContents),
            eq(7),
            eq(3)
        );
    }

    @Test
    void startVerificationFailsClosedWhenAiQuestionGenerationFails() {
        VerificationService verificationService = new VerificationService(
            gitHubService,
            fileFilterService,
            codeExtractorService,
            userRepository,
            sessionRepository,
            questionRepository,
            new ObjectMapper(),
            aiGatewayService
        );

        ReflectionTestUtils.setField(verificationService, "bypassVerificationLimit", true);
        ReflectionTestUtils.setField(verificationService, "mixedQuestionsEnabled", true);
        ReflectionTestUtils.setField(verificationService, "phase2TotalQuestions", 7);
        ReflectionTestUtils.setField(verificationService, "phase2ConceptualQuestions", 3);

        User user = User.builder()
            .id(404L)
            .githubUserId("404")
            .githubUsername("fallback-dev")
            .githubAccessToken("token2")
            .plan(User.Plan.FREE)
            .build();

        StartVerificationRequest request = new StartVerificationRequest();
        request.setRepoOwner("Org2");
        request.setRepoName("Repo2");

        Map<String, String> fileContents = Map.of(
            "src/C.java", "class C {}",
            "src/D.java", "class D {}"
        );

        when(userRepository.findById(404L)).thenReturn(Optional.of(user));
        when(gitHubService.getRepositoryInfo("token2", "Org2", "Repo2"))
            .thenReturn(Map.of("description", "desc2", "language", "Java"));
        when(gitHubService.getRepositoryFileTree("token2", "Org2", "Repo2"))
            .thenReturn(List.of("src/C.java", "src/D.java"));
        when(fileFilterService.filterAndRankFiles(List.of("src/C.java", "src/D.java")))
            .thenReturn(List.of("src/C.java", "src/D.java"));
        when(gitHubService.fetchFileContents("token2", "Org2", "Repo2", List.of("src/C.java", "src/D.java")))
            .thenReturn(fileContents);
        when(fileFilterService.detectFrameworks(anyList(), eq(fileContents)))
            .thenReturn(List.of("Spring Boot"));
        when(fileFilterService.detectPrimaryLanguage(List.of("src/C.java", "src/D.java")))
            .thenReturn("Java");
        when(codeExtractorService.extractCodeSummary(fileContents, "Java"))
            .thenReturn("summary2");
        when(sessionRepository.save(any(VerificationSession.class))).thenAnswer(invocation -> {
            VerificationSession session = invocation.getArgument(0);
            session.setId(701L);
            return session;
        });
        when(aiGatewayService.generateQuestionsViaAI(
            any(VerificationSession.class),
            eq("summary2"),
            eq("Java"),
            eq(List.of("Spring Boot")),
            eq(fileContents),
            eq(7),
            eq(3)
        )).thenThrow(new ApiException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "AI_QUESTION_GENERATION_UNAVAILABLE",
            "AI question generation service is currently unavailable."
        ));

        ApiException ex = assertThrows(ApiException.class,
            () -> verificationService.startVerification(404L, request));

        assertEquals("AI_QUESTION_GENERATION_UNAVAILABLE", ex.getCode());
    }

    private Question buildQuestion(int questionNumber,
                                   Question.Difficulty difficulty,
                                   Question.QuestionType questionType,
                                   String fileReference) {
        return Question.builder()
            .questionNumber(questionNumber)
            .difficulty(difficulty)
            .questionType(questionType)
            .fileReference(fileReference)
            .questionText("Q" + questionNumber)
            .codeContext("ctx")
            .build();
    }
}
