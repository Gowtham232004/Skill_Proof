package com.skillproof.backend_core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_gap_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillGapReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VerificationSession session;

    @Column(name = "gaps_json", columnDefinition = "TEXT")
    private String gapsJson; // stored as JSON string

    @Column(name = "overall_health_score")
    private Integer overallHealthScore;

    @Column(name = "critical_count")
    private Integer criticalCount;

    @Column(name = "important_count")
    private Integer importantCount;

    @Column(name = "minor_count")
    private Integer minorCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
