package com.skillproof.backend_core.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillproof.backend_core.dto.request.GeneratePrReviewRequest;
import com.skillproof.backend_core.dto.request.SubmitPrReviewRequest;
import com.skillproof.backend_core.dto.response.PrReviewResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.service.PrReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pr-review")
@RequiredArgsConstructor
@Validated
public class PrReviewController {

    private final PrReviewService prReviewService;

    @PostMapping("/generate")
    public ResponseEntity<PrReviewResponse> generate(
            @Valid @RequestBody GeneratePrReviewRequest request,
            Authentication authentication) {

        Long recruiterId = extractAuthenticatedUserId(authentication, "generate PR review");
        PrReviewResponse response = prReviewService.generateReview(recruiterId, request.getBadgeToken());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{token}")
    public ResponseEntity<PrReviewResponse> open(@PathVariable String token) {
        return ResponseEntity.ok(prReviewService.openReview(token));
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<PrReviewResponse> submit(
            @PathVariable String token,
            @Valid @RequestBody SubmitPrReviewRequest request) {

        PrReviewResponse response = prReviewService.submitReview(token, request.getComments(), request.getTimeTaken());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{token}/result")
    public ResponseEntity<PrReviewResponse> result(
            @PathVariable String token,
            Authentication authentication) {

        Long recruiterId = extractAuthenticatedUserId(authentication, "view PR review result");
        return ResponseEntity.ok(prReviewService.getResult(recruiterId, token));
    }

    private Long extractAuthenticatedUserId(Authentication authentication, String action) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required to " + action);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number n) {
            return n.longValue();
        }

        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid authenticated principal for " + action);
    }
}
