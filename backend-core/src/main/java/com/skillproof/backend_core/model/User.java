package com.skillproof.backend_core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // GitHub's numeric user ID — unique identifier from GitHub
    @Column(name = "github_user_id", unique = true, nullable = false)
    private String githubUserId;

    @Column(name = "github_username", nullable = false)
    private String githubUsername;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "email")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private Role role = Role.DEVELOPER;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    @Builder.Default
    private Plan plan = Plan.FREE;

    // how many verifications used this month (free tier limit = 3)
    @Column(name = "monthly_verifications_used")
    @Builder.Default
    private Integer monthlyVerificationsUsed = 0;

    @Column(name = "github_access_token")
    private String githubAccessToken;  // stored to call GitHub API on their behalf

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Enums ────────────────────────────────────────────────────────────────

    public enum Role {
        DEVELOPER,
        RECRUITER,
        COMPANY,
        ADMIN
    }

    public enum Plan {
        FREE,       // 3 verifications/month
        PRO,        // unlimited
        ENTERPRISE  // API access + batch
    }
}