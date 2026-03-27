package com.skillproof.backend_core.repository;



import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.VerificationSession;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySessionOrderByQuestionNumber(VerificationSession session);

    List<Question> findBySessionIdOrderByQuestionNumber(Long sessionId);

    long countBySessionId(Long sessionId);
}