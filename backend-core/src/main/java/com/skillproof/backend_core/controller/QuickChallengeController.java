package com.skillproof.backend_core.controller;

import java.util.List;
import java.util.Map;

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

import com.skillproof.backend_core.dto.response.QuickChallengeResponse;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.service.QuickChallengeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/quick-challenge")
@RequiredArgsConstructor
@Validated
public class QuickChallengeController {

    private final QuickChallengeService quickChallengeService;

    @PostMapping("/generate")
    public ResponseEntity<QuickChallengeResponse> generate(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long recruiterId = extractAuthenticatedUserId(authentication, "generateQuickChallenge");
        String badgeToken = body.get("badgeToken");
        return ResponseEntity.ok(quickChallengeService.generateChallenge(recruiterId, badgeToken));
    }

    @GetMapping("/{token}")
    public ResponseEntity<QuickChallengeResponse> open(@PathVariable String token) {
        return ResponseEntity.ok(quickChallengeService.openChallenge(token));
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<QuickChallengeResponse> submit(
            @PathVariable String token,
            @RequestBody Map<String, Object> body) {
        String answer = String.valueOf(body.getOrDefault("answer", ""));
        Integer tabSwitches = parseInteger(body.get("tabSwitches"), 0);
        Integer timeTaken = parseInteger(body.get("timeTaken"), 600);

        return ResponseEntity.ok(
            quickChallengeService.submitAnswer(token, answer, tabSwitches, timeTaken)
        );
    }

    @GetMapping("/{token}/result")
    public ResponseEntity<QuickChallengeResponse> getResult(
            @PathVariable String token,
            Authentication authentication) {
        Long recruiterId = extractAuthenticatedUserId(authentication, "getQuickChallengeResult");
        return ResponseEntity.ok(quickChallengeService.getChallengeResult(recruiterId, token));
    }

    @GetMapping("/my-challenges")
    public ResponseEntity<List<QuickChallengeResponse>> myChallenges(Authentication authentication) {
        Long recruiterId = extractAuthenticatedUserId(authentication, "listQuickChallenges");
        return ResponseEntity.ok(quickChallengeService.getRecruiterChallenges(recruiterId));
    }

    private Integer parseInteger(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Long extractAuthenticatedUserId(Authentication authentication, String action) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required for " + action);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }

        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid authenticated principal for " + action);
    }
}
