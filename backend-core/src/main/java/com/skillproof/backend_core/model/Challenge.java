package com.skillproof.backend_core.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "language", nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_mode", nullable = false)
    @Builder.Default
    private ChallengeMode challengeMode = ChallengeMode.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_mode", nullable = false)
    @Builder.Default
    private AccessMode accessMode = AccessMode.OPEN;

    @Column(name = "assigned_candidates_json", columnDefinition = "LONGTEXT")
    private String assignedCandidatesJson;

    @Column(name = "starter_code", columnDefinition = "LONGTEXT")
    private String starterCode;

    @Column(name = "reference_solution", columnDefinition = "LONGTEXT")
    private String referenceSolution;

    @Column(name = "test_cases_json", columnDefinition = "LONGTEXT")
    private String testCasesJson;

    @Column(name = "source_badge_token", length = 120)
    private String sourceBadgeToken;

    @Column(name = "source_repo_name", length = 255)
    private String sourceRepoName;

    @Column(name = "source_file_path", length = 500)
    private String sourceFilePath;

    @Column(name = "source_snippet_hash", length = 64)
    private String sourceSnippetHash;

    @Column(name = "generation_reason", length = 500)
    private String generationReason;

    @Column(name = "time_limit_seconds", nullable = false)
    @Builder.Default
    private Integer timeLimitSeconds = 10;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ChallengeMode {
        MANUAL,
        REPO_GROUNDED
    }

    public enum AccessMode {
        OPEN,
        ASSIGNED
    }
}
