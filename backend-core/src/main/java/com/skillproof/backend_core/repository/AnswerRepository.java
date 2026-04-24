package com.skillproof.backend_core.repository;



import com.skillproof.backend_core.model.Answer;
import com.skillproof.backend_core.model.Question;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByQuestionSessionId(Long sessionId);

    List<Answer> findByQuestionSessionIdOrderByQuestionQuestionNumberAsc(Long sessionId);

    @Query("""
        select a
        from Answer a
        join fetch a.question q
        where q.session.id = :sessionId
        order by q.questionNumber asc
        """)
    List<Answer> findBySessionIdWithQuestionOrderByQuestionNumberAsc(Long sessionId);

    Optional<Answer> findByQuestionSessionIdAndQuestionQuestionNumber(Long sessionId, Integer questionNumber);

    Optional<Answer> findByQuestion(Question question);
}