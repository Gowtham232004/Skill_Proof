package com.skillproof.backend_core.model;



import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "badges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VerificationSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // HMAC-SHA256 signed token — used in public URL
    // e.g. skillproof.dev/badge/sp_abc123xyz
    @Column(name = "verification_token", unique = true, nullable = false)
    private String verificationToken;

    // Overall score 0–100
    @Column(name = "overall_score")
    private Integer overallScore;

    // Raw technical score before integrity adjustment.
    @Column(name = "technical_score")
    private Integer technicalScore;

    // Score after soft integrity deduction.
    @Column(name = "integrity_adjusted_score")
    private Integer integrityAdjustedScore;

    @Column(name = "integrity_penalty_total")
    private Integer integrityPenaltyTotal;

    @Column(name = "integrity_penalty_breakdown", columnDefinition = "TEXT")
    private String integrityPenaltyBreakdown;

    // Skill dimension scores 0–100
    @Column(name = "backend_score")
    private Integer backendScore;

    @Column(name = "api_design_score")
    private Integer apiDesignScore;

    @Column(name = "error_handling_score")
    private Integer errorHandlingScore;

    @Column(name = "code_quality_score")
    private Integer codeQualityScore;

    @Column(name = "documentation_score")
    private Integer documentationScore;

    // Confidence tier based on skips, answer length, and score consistency.
    @Column(name = "confidence_tier")
    private String confidenceTier;

    // Transparency signals for recruiter judgment.
    @Column(name = "tab_switches")
    private Integer tabSwitches;

    @Column(name = "paste_count")
    private Integer pasteCount;

    @Column(name = "avg_answer_seconds")
    private Integer avgAnswerSeconds;

    // Can be revoked by the developer
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "issued_at", updatable = false)
    private LocalDateTime issuedAt;
}