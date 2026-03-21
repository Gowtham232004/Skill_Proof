package com.skillproof.backend_core.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.response.AuthResponse;
import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.BadgeRepository;
import com.skillproof.backend_core.repository.UserRepository;
import com.skillproof.backend_core.service.AuthService;
import com.skillproof.backend_core.service.GitHubService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final GitHubService gitHubService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final BadgeRepository badgeRepository;

    @GetMapping("/github")
    public ResponseEntity<Map<String, String>> getGitHubAuthUrl() {
        String url = gitHubService.buildAuthorizationUrl();
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/github/callback")
    public RedirectView githubCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        try {
            log.info("GitHub OAuth callback received with code: {}", code.substring(0, 8) + "...");
            AuthResponse response = authService.handleGitHubCallback(code);
            log.info("✅ User authenticated: {}", response.getGithubUsername());
            
            // Redirect to frontend callback page with token in query param
            String authData = objectMapper.writeValueAsString(response);
            String encoded = URLEncoder.encode(authData, StandardCharsets.UTF_8);
            String redirectUrl = "http://localhost:3000/auth/callback?data=" + encoded;
            
            log.info("🔄 Redirecting to frontend callback: http://localhost:3000/auth/callback?data=...");
            return new RedirectView(redirectUrl);
        } catch (Exception e) {
            log.error("❌ GitHub callback error: {}", e.getMessage(), e);
            return new RedirectView("http://localhost:3000/?error=auth_failed");
        }
    }

    @GetMapping("/repos")
    public ResponseEntity<List<Map<String, Object>>> getUserRepos(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        List<Map<String, Object>> repos = gitHubService.getUserRepositories(
            user.getGithubAccessToken());
        return ResponseEntity.ok(repos);
    }
    @GetMapping("/recruiter/candidates")
    public ResponseEntity<List<Map<String, Object>>> getCandidates() {
        // Return all badges as candidate list
        List<Badge> badges = badgeRepository.findAll();
        List<Map<String, Object>> result = badges.stream().map(b -> {
            Map<String, Object> c = new java.util.HashMap<>();
            c.put("sessionId", b.getId());
            c.put("githubUsername", b.getUser().getGithubUsername());
            c.put("avatarUrl", b.getUser().getAvatarUrl());
            c.put("displayName", b.getUser().getDisplayName());
            c.put("repoName", b.getSession().getRepoName());
            c.put("overallScore", b.getOverallScore());
            c.put("backendScore", b.getBackendScore());
            c.put("apiDesignScore", b.getApiDesignScore());
            c.put("errorHandlingScore", b.getErrorHandlingScore());
            c.put("codeQualityScore", b.getCodeQualityScore());
            c.put("documentationScore", b.getDocumentationScore());
            c.put("badgeToken", b.getVerificationToken());
            c.put("issuedAt", b.getIssuedAt());
            c.put("primaryLanguage", b.getSession().getRepoLanguage());
            return c;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(Map.of("message", "Token is valid"));
    }
}