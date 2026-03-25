package com.skillproof.backend_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.StartVerificationRequest;
import com.skillproof.backend_core.exception.ApiException;
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
}
