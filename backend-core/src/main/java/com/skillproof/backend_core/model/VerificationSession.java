package com.skillproof.backend_core.model;


import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "verification_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "repo_owner", nullable = false)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "repo_description", columnDefinition = "TEXT")
    private String repoDescription;

    @Column(name = "repo_language")
    private String repoLanguage;

    // JSON array of framework names e.g. ["Spring Boot","MySQL","JWT"]
    @Column(name = "frameworks_detected", columnDefinition = "TEXT")
    private String frameworksDetected;

    // Number of source files actually analyzed
    @Column(name = "files_analyzed")
    private Integer filesAnalyzed;

    // The extracted code summary sent to AI — stored for re-use
    @Column(name = "code_summary", columnDefinition = "LONGTEXT")
    private String codeSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Status status = Status.IN_PROGRESS;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum Status {
        IN_PROGRESS,   // questions fetched, waiting for answers
        COMPLETED,     // answers submitted, badge issued
        ABANDONED      // user left without submitting
    }
}
