package com.skillproof.backend_core.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillproof.backend_core.dto.request.CreateChallengeRequest;
import com.skillproof.backend_core.dto.request.SubmitChallengeRequest;
import com.skillproof.backend_core.dto.response.ChallengeResponse;
import com.skillproof.backend_core.dto.response.ChallengeSubmissionResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.service.ChallengeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @PostMapping
    public ResponseEntity<ChallengeResponse> createChallenge(
            @Valid @RequestBody CreateChallengeRequest request,
            Authentication authentication) {

        Long recruiterId = extractAuthenticatedUserId(authentication, "createChallenge");
        return ResponseEntity.ok(challengeService.createChallenge(recruiterId, request));
    }

    @GetMapping("/{challengeId}")
    public ResponseEntity<ChallengeResponse> getChallenge(@PathVariable Long challengeId) {
        return ResponseEntity.ok(challengeService.getChallenge(challengeId));
    }

    @PostMapping("/{challengeId}/submit")
    public ResponseEntity<ChallengeSubmissionResponse> submitChallenge(
            @PathVariable Long challengeId,
            @Valid @RequestBody SubmitChallengeRequest request,
            Authentication authentication) {

        Long candidateId = extractAuthenticatedUserId(authentication, "submitChallenge");
        return ResponseEntity.ok(challengeService.submitChallenge(challengeId, candidateId, request));
    }

    @GetMapping("/{challengeId}/submissions")
    public ResponseEntity<List<ChallengeSubmissionResponse>> getChallengeSubmissions(
            @PathVariable Long challengeId,
            Authentication authentication) {

        Long recruiterId = extractAuthenticatedUserId(authentication, "getChallengeSubmissions");
        return ResponseEntity.ok(challengeService.getChallengeSubmissions(challengeId, recruiterId));
    }

    private Long extractAuthenticatedUserId(Authentication authentication, String action) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Authentication is required for " + action
            );
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }

        throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "Invalid authenticated principal for " + action
        );
    }
}
