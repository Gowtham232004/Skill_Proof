package com.skillproof.backend_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.CreateLiveSessionRequest;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.LiveSession;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.LiveSessionRepository;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LiveSessionServiceTests {

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AiGatewayService aiGatewayService;

    @Test
    void createLiveSessionReturnsConflictWithRequiredDetailsWhenActiveSessionExists() {
        LiveSessionService service = new LiveSessionService(
            liveSessionRepository,
            badgeRepository,
            userRepository,
            questionRepository,
            aiGatewayService,
            new ObjectMapper()
        );

        Long recruiterId = 77L;
        String badgeToken = "badge-token-123";

        User recruiter = User.builder()
            .id(recruiterId)
            .githubUserId("77")
            .githubUsername("recruiter-user")
            .role(User.Role.RECRUITER)
            .build();

        VerificationSession verificationSession = VerificationSession.builder()
            .id(501L)
            .repoName("demo-repo")
            .build();

        Badge badge = Badge.builder()
            .verificationToken(badgeToken)
            .session(verificationSession)
            .user(User.builder().githubUsername("candidate-user").build())
            .build();

        LiveSession activeSession = LiveSession.builder()
            .sessionCode("AB12CD")
            .badgeToken(badgeToken)
            .recruiter(User.builder().id(recruiterId).build())
            .status(LiveSession.LiveSessionStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        CreateLiveSessionRequest request = new CreateLiveSessionRequest();
        request.setBadgeToken(badgeToken);

        when(userRepository.findById(recruiterId)).thenReturn(Optional.of(recruiter));
        when(badgeRepository.findByVerificationToken(badgeToken)).thenReturn(Optional.of(badge));
        when(liveSessionRepository.findByBadgeToken(badgeToken)).thenReturn(List.of(activeSession));

        ApiException ex = assertThrows(ApiException.class, () -> service.createLiveSession(recruiterId, request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("LIVE_SESSION_ALREADY_EXISTS", ex.getCode());

        Map<String, Object> details = ex.getDetails();
        assertNotNull(details);
        assertEquals("AB12CD", details.get("existingSessionCode"));
        assertEquals("ACTIVE", details.get("status"));
        assertEquals("/recruiter/live/AB12CD", details.get("recruiterUrl"));
    }

    @Test
    void createLiveSessionRejectsWhenActiveSessionBelongsToAnotherRecruiter() {
        LiveSessionService service = new LiveSessionService(
            liveSessionRepository,
            badgeRepository,
            userRepository,
            questionRepository,
            aiGatewayService,
            new ObjectMapper()
        );

        Long recruiterId = 88L;
        Long ownerRecruiterId = 99L;
        String badgeToken = "badge-token-456";

        User requester = User.builder()
            .id(recruiterId)
            .githubUserId("88")
            .githubUsername("requester")
            .role(User.Role.RECRUITER)
            .build();

        VerificationSession verificationSession = VerificationSession.builder()
            .id(601L)
            .repoName("another-repo")
            .build();

        Badge badge = Badge.builder()
            .verificationToken(badgeToken)
            .session(verificationSession)
            .user(User.builder().githubUsername("candidate-user").build())
            .build();

        LiveSession activeSession = LiveSession.builder()
            .sessionCode("ZX12CV")
            .badgeToken(badgeToken)
            .recruiter(User.builder().id(ownerRecruiterId).build())
            .status(LiveSession.LiveSessionStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        CreateLiveSessionRequest request = new CreateLiveSessionRequest();
        request.setBadgeToken(badgeToken);

        when(userRepository.findById(recruiterId)).thenReturn(Optional.of(requester));
        when(badgeRepository.findByVerificationToken(badgeToken)).thenReturn(Optional.of(badge));
        when(liveSessionRepository.findByBadgeToken(badgeToken)).thenReturn(List.of(activeSession));

        ApiException ex = assertThrows(ApiException.class, () -> service.createLiveSession(recruiterId, request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("LIVE_SESSION_OWNED_BY_ANOTHER_RECRUITER", ex.getCode());

        Map<String, Object> details = ex.getDetails();
        assertNotNull(details);
        assertEquals("ACTIVE", details.get("status"));
    }
}
