package com.skillproof.backend_core.model;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "answer_text", columnDefinition = "TEXT", nullable = false)
    private String answerText;

    // AI evaluation scores — each 0 to 10
    @Column(name = "accuracy_score")
    private Integer accuracyScore;

    @Column(name = "depth_score")
    private Integer depthScore;

    @Column(name = "specificity_score")
    private Integer specificityScore;

    // Weighted composite: accuracy*0.4 + depth*0.3 + specificity*0.3
    @Column(name = "composite_score")
    private Double compositeScore;

    // AI explanation of why this score was given
    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    // Cached recruiter-facing AI reference answer (generated on first request)
    @Column(name = "reference_answer", columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(name = "review_checkpoints_json", columnDefinition = "TEXT")
    private String reviewCheckpointsJson;

    @Column(name = "reference_answer_generated_at")
    private LocalDateTime referenceAnswerGeneratedAt;
}