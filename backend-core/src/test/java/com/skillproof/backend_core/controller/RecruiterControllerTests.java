package com.skillproof.backend_core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.skillproof.backend_core.dto.request.CompareCandidatesRequest;
import com.skillproof.backend_core.dto.response.BadgeResponse;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.AnswerRepository;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.UserRepository;
import com.skillproof.backend_core.service.AiGatewayService;
import com.skillproof.backend_core.service.BadgeService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RecruiterControllerTests {

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BadgeService badgeService;

    @Mock
    private AiGatewayService aiGatewayService;

    @Mock
    private Authentication authentication;

    @Test
    void getCandidatesIncludesConceptGapFlag() {
        RecruiterController controller = new RecruiterController(
            badgeRepository,
            answerRepository,
            userRepository,
            badgeService,
            aiGatewayService,
            new ObjectMapper()
        );

        User recruiter = User.builder()
            .id(1L)
            .githubUsername("recruiter")
            .role(User.Role.RECRUITER)
            .build();

        User candidateUser = User.builder()
            .id(2L)
            .githubUsername("candidate")
            .displayName("Candidate One")
            .build();

        VerificationSession session = VerificationSession.builder()
            .repoName("repo")
            .repoOwner("owner")
            .repoLanguage("Java")
            .build();

        Badge badge = Badge.builder()
            .id(9L)
            .user(candidateUser)
            .session(session)
            .verificationToken("token_1")
            .overallScore(78)
            .backendScore(80)
            .apiDesignScore(75)
            .errorHandlingScore(70)
            .codeQualityScore(82)
            .documentationScore(65)
            .isActive(true)
            .build();

        BadgeResponse detail = BadgeResponse.builder()
            .valid(true)
            .scoreByQuestionType(Map.of(
                "CODE_GROUNDED", 82,
                "CONCEPTUAL", 60
            ))
            .weightedScoringEnabled(true)
            .codeWeightPercent(60)
            .conceptualWeightPercent(40)
            .followUpRequiredCount(2)
            .followUpAnsweredCount(1)
            .build();

        when(authentication.getPrincipal()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(recruiter));
        when(badgeRepository.findAllActiveWithUserAndSession()).thenReturn(List.of(badge));
        when(badgeService.getBadgeByToken("token_1")).thenReturn(detail);

        ResponseEntity<List<Map<String, Object>>> response = controller.getCandidates(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        Map<String, Object> row = response.getBody().get(0);
        assertTrue((Boolean) row.get("conceptGapFlag"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> scoreByType = (Map<String, Integer>) row.get("scoreByQuestionType");
        assertEquals(82, scoreByType.get("CODE_GROUNDED"));
        assertEquals(60, scoreByType.get("CONCEPTUAL"));
        assertTrue((Boolean) row.get("weightedScoringEnabled"));
        assertEquals(60, row.get("codeWeightPercent"));
        assertEquals(40, row.get("conceptualWeightPercent"));
        assertEquals(2, row.get("followUpRequiredCount"));
        assertEquals(1, row.get("followUpAnsweredCount"));
    }

    @Test
    void compareCandidatesReturnsUniqueListAndTopCandidate() {
        RecruiterController controller = new RecruiterController(
            badgeRepository,
            answerRepository,
            userRepository,
            badgeService,
            aiGatewayService,
            new ObjectMapper()
        );

        User recruiter = User.builder()
            .id(1L)
            .githubUsername("recruiter")
            .role(User.Role.RECRUITER)
            .build();

        when(authentication.getPrincipal()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(recruiter));

        BadgeResponse first = BadgeResponse.builder()
            .valid(true)
            .verificationToken("tok_a")
            .githubUsername("cand-a")
            .displayName("Candidate A")
            .repoName("repo-a")
            .overallScore(80)
            .technicalScore(82)
            .integrityAdjustedScore(78)
            .executionAdjustedScore(79)
            .build();

        BadgeResponse second = BadgeResponse.builder()
            .valid(true)
            .verificationToken("tok_b")
            .githubUsername("cand-b")
            .displayName("Candidate B")
            .repoName("repo-b")
            .overallScore(86)
            .technicalScore(88)
            .integrityAdjustedScore(84)
            .executionAdjustedScore(90)
            .build();

        when(badgeService.getBadgeByTokenForRecruiter("tok_a")).thenReturn(first);
        when(badgeService.getBadgeByTokenForRecruiter("tok_b")).thenReturn(second);

        CompareCandidatesRequest request = new CompareCandidatesRequest();
        request.setBadgeTokens(List.of(" tok_a ", "tok_b", "tok_a", ""));

        ResponseEntity<Map<String, Object>> response = controller.compareCandidates(request, authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().get("comparedCount"));
        assertEquals("tok_b", response.getBody().get("topCandidateToken"));
    }
}
