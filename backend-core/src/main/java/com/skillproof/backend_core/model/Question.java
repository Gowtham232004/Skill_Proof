package com.skillproof.backend_core.model;



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
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VerificationSession session;

    // 1 through 5
    @Column(name = "question_number", nullable = false)
    private Integer questionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private Difficulty difficulty;

    // Which file this question is about e.g. "OrderService.java"
    @Column(name = "file_reference")
    private String fileReference;

    @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType;

    // The actual code snippet used to generate this question
    @Column(name = "code_context", columnDefinition = "LONGTEXT")
    private String codeContext;

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public enum QuestionType {
        CODE_GROUNDED,
        CONCEPTUAL,
        PATTERN,
        EDGE_CASE
    }
}