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
@Table(name = "quick_challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "badge_token", nullable = false)
    private String badgeToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @Column(name = "selected_file_path")
    private String selectedFilePath;

    @Column(name = "code_snippet", columnDefinition = "LONGTEXT")
    private String codeSnippet;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "candidate_username")
    private String candidateUsername;

    @Column(name = "repo_name")
    private String repoName;

    @Column(name = "candidate_answer", columnDefinition = "TEXT")
    private String candidateAnswer;

    @Column(name = "accuracy_score")
    private Integer accuracyScore;

    @Column(name = "depth_score")
    private Integer depthScore;

    @Column(name = "specificity_score")
    private Integer specificityScore;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private QuickChallengeStatus status = QuickChallengeStatus.PENDING;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "tab_switch_count")
    @Builder.Default
    private Integer tabSwitchCount = 0;

    @Column(name = "challenge_token", unique = true, nullable = false)
    private String challengeToken;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    @Builder.Default
    private LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum QuickChallengeStatus {
        PENDING,
        ACTIVE,
        COMPLETED,
        EXPIRED
    }
}
