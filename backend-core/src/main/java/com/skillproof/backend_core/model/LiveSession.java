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
@Table(name = "live_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", unique = true, nullable = false, length = 6)
    private String sessionCode;

    @Column(name = "badge_token", nullable = false)
    private String badgeToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @Column(name = "candidate_email")
    private String candidateEmail;

    @Column(name = "candidate_username")
    private String candidateUsername;

    @Column(name = "repo_name")
    private String repoName;

    @Column(name = "current_revealed_question")
    @Builder.Default
    private Integer currentRevealedQuestion = 0;

    @Column(name = "live_answers_json", columnDefinition = "LONGTEXT")
    private String liveAnswersJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LiveSessionStatus status = LiveSessionStatus.PENDING;

    @Column(name = "live_score")
    private Integer liveScore;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    @Builder.Default
    private LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum LiveSessionStatus {
        PENDING,
        ACTIVE,
        COMPLETED,
        EXPIRED
    }
}
