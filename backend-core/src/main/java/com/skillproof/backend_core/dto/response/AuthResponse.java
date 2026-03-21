package com.skillproof.backend_core.dto.response;



import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String tokenType;       // always "Bearer"
    private Long userId;
    private String githubUsername;
    private String avatarUrl;
    private String displayName;
    private String role;
    private String plan;
    private boolean isNewUser;      // true = first time login
}
