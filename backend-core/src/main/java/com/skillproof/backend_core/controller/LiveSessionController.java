package com.skillproof.backend_core.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillproof.backend_core.dto.request.CreateLiveSessionRequest;
import com.skillproof.backend_core.dto.request.SubmitLiveAnswerRequest;
import com.skillproof.backend_core.dto.response.LiveAnswerSubmitResponse;
import com.skillproof.backend_core.dto.response.LiveQuestionRevealResponse;
import com.skillproof.backend_core.dto.response.LiveSessionResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.service.LiveSessionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
@Validated
@Slf4j
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    @PostMapping("/sessions")
    public ResponseEntity<LiveSessionResponse> createLiveSession(
            @Valid @RequestBody CreateLiveSessionRequest request,
            Authentication authentication) {

        Long recruiterId = extractAuthenticatedUserId(authentication, "createLiveSession");
        LiveSessionResponse response = liveSessionService.createLiveSession(recruiterId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionCode}/reveal-next")
    public ResponseEntity<LiveQuestionRevealResponse> revealNextQuestion(
            @PathVariable String sessionCode,
            Authentication authentication) {

        Long recruiterId = extractAuthenticatedUserId(authentication, "revealNextQuestion");
        LiveQuestionRevealResponse response = liveSessionService.revealNextQuestion(sessionCode, recruiterId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionCode}/status")
    public ResponseEntity<LiveSessionResponse> getSessionStatus(@PathVariable String sessionCode) {
        LiveSessionResponse response = liveSessionService.getSessionStatus(sessionCode);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionCode}/questions/{questionNumber}")
    public ResponseEntity<Map<String, Object>> getCandidateQuestion(
            @PathVariable String sessionCode,
            @PathVariable Integer questionNumber) {

        Map<String, Object> payload = liveSessionService.getCandidateQuestion(sessionCode, questionNumber);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{sessionCode}/questions/{questionNumber}/answer")
    public ResponseEntity<LiveAnswerSubmitResponse> submitLiveAnswer(
            @PathVariable String sessionCode,
            @PathVariable Integer questionNumber,
            @Valid @RequestBody SubmitLiveAnswerRequest request) {

        LiveAnswerSubmitResponse response = liveSessionService.submitLiveAnswer(
            sessionCode,
            questionNumber,
            request.getAnswerText()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionCode}/answers")
    public ResponseEntity<List<Map<String, Object>>> getSessionAnswers(
            @PathVariable String sessionCode,
            Authentication authentication) {

        Long recruiterId = extractAuthenticatedUserId(authentication, "getSessionAnswers");
        try {
            List<Map<String, Object>> answers = liveSessionService.getLiveSessionAnswers(sessionCode, recruiterId);
            log.info("Live answers request succeeded: sessionCode={} recruiterId={} count={}", sessionCode, recruiterId, answers.size());
            return ResponseEntity.ok(answers);
        } catch (Exception ex) {
            log.error("Live answers request failed: sessionCode={} recruiterId={}", sessionCode, recruiterId, ex);
            throw ex;
        }
    }

    private Long extractAuthenticatedUserId(Authentication authentication, String action) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Authentication is required for " + action
            );
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }

        throw new ApiException(
            org.springframework.http.HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "Invalid authenticated principal for " + action
        );
    }
}
