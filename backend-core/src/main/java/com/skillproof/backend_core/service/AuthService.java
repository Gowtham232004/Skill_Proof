package com.skillproof.backend_core.service;


import com.skillproof.backend_core.dto.response.AuthResponse;
import com.skillproof.backend_core.dto.response.GitHubUserResponse;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.repository.UserRepository;
import com.skillproof.backend_core.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final GitHubService gitHubService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    // Called when GitHub redirects back to our callback URL with a `code`
    @Transactional
    public AuthResponse handleGitHubCallback(String code) {

        // 1. Exchange code for GitHub access token
        String githubAccessToken = gitHubService.exchangeCodeForToken(code);
        log.info("Successfully exchanged GitHub code for access token");

        // 2. Get user info from GitHub
        GitHubUserResponse githubUser = gitHubService.getUserInfo(githubAccessToken);
        log.info("GitHub user fetched: {}", githubUser.getLogin());

        // 3. Find existing user or create new one
        boolean isNewUser = !userRepository.existsByGithubUserId(
                String.valueOf(githubUser.getId())
        );

        User user = userRepository.findByGithubUserId(String.valueOf(githubUser.getId()))
                .orElseGet(() -> {
                    // First time login — create new user
                    log.info("Creating new user for GitHub user: {}", githubUser.getLogin());
                    return User.builder()
                            .githubUserId(String.valueOf(githubUser.getId()))
                            .githubUsername(githubUser.getLogin())
                            .displayName(githubUser.getName() != null ?
                                    githubUser.getName() : githubUser.getLogin())
                            .email(githubUser.getEmail())
                            .avatarUrl(githubUser.getAvatarUrl())
                            .role(User.Role.DEVELOPER)
                            .plan(User.Plan.FREE)
                            .build();
                });

        // 4. Always update GitHub access token and avatar (may have changed)
        user.setGithubAccessToken(githubAccessToken);
        user.setAvatarUrl(githubUser.getAvatarUrl());
        user = userRepository.save(user);

        // 5. Generate JWT for our app
        String jwt = jwtUtil.generateToken(
                user.getId(),
                user.getGithubUsername(),
                user.getRole().name()
        );

        log.info("Auth successful for user: {} (new: {})", user.getGithubUsername(), isNewUser);

        // 6. Return everything frontend needs
        return AuthResponse.builder()
                .accessToken(jwt)
                .tokenType("Bearer")
                .userId(user.getId())
                .githubUsername(user.getGithubUsername())
                .avatarUrl(user.getAvatarUrl())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .plan(user.getPlan().name())
                .isNewUser(isNewUser)
                .build();
    }
}
