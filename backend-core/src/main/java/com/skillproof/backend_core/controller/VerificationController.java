package com.skillproof.backend_core.controller;



import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillproof.backend_core.dto.request.StartVerificationRequest;
import com.skillproof.backend_core.dto.request.SubmitAnswersRequest;
import com.skillproof.backend_core.dto.request.SubmitFollowUpAnswersRequest;
import com.skillproof.backend_core.dto.response.VerificationResultResponse;
import com.skillproof.backend_core.dto.response.VerificationStartResponse;
import com.skillproof.backend_core.service.SubmitAnswersService;
import com.skillproof.backend_core.service.VerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
@Slf4j
public class VerificationController {

    private final VerificationService verificationService;
    private final SubmitAnswersService submitAnswersService;

    // POST /api/verify/start
    // Body: { "repoOwner": "Gowtham232004", "repoName": "food-ordering-app" }
    // Auth: Bearer {jwt}
    @PostMapping("/submit")
public ResponseEntity<VerificationResultResponse> submitAnswers(
        @Valid @RequestBody SubmitAnswersRequest request,
        Authentication authentication) {

    Long userId = (Long) authentication.getPrincipal();
    log.info("Answer submission from user {} for session {}",
        userId, request.getSessionId());

    VerificationResultResponse response =
        submitAnswersService.submitAnswers(userId, request);

    return ResponseEntity.ok(response);
}

    @PostMapping("/submit-followups")
    public ResponseEntity<VerificationResultResponse> submitFollowUpAnswers(
            @Valid @RequestBody SubmitFollowUpAnswersRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        log.info("Follow-up submission from user {} for session {}",
            userId, request.getSessionId());

        VerificationResultResponse response =
            submitAnswersService.submitFollowUpAnswers(userId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/start")
    public ResponseEntity<VerificationStartResponse> startVerification(
            @Valid @RequestBody StartVerificationRequest request,
            Authentication authentication) {

        // JWT filter puts userId as the principal
        Long userId = (Long) authentication.getPrincipal();
        log.info("Verification start request from user {} for repo {}/{}",
            userId, request.getRepoOwner(), request.getRepoName());

        VerificationStartResponse response =
            verificationService.startVerification(userId, request);

        return ResponseEntity.ok(response);
    }
}
