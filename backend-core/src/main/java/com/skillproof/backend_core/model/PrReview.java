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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pr_reviews")
@Getter
@Setter
@NoArgsConstructor
public class PrReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "badge_token", nullable = false)
    private String badgeToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "original_code", columnDefinition = "TEXT")
    private String originalCode;

    @Column(name = "modified_code", columnDefinition = "TEXT")
    private String modifiedCode;

    @Column(name = "bug_description", columnDefinition = "TEXT")
    private String bugDescription;

    @Column(name = "candidate_username")
    private String candidateUsername;

    @Column(name = "repo_name")
    private String repoName;

    @Column(name = "review_token", unique = true, nullable = false)
    private String reviewToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrReviewStatus status = PrReviewStatus.PENDING;

    @Column(name = "candidate_review_json", columnDefinition = "TEXT")
    private String candidateReviewJson = "[]";

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "bugs_found_count")
    private Integer bugsFoundCount = 0;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt = LocalDateTime.now().plusHours(48);

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum PrReviewStatus {
        PENDING,
        ACTIVE,
        COMPLETED,
        EXPIRED
    }
}
