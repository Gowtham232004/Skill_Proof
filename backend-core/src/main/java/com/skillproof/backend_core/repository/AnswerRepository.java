package com.skillproof.backend_core.repository;



import com.skillproof.backend_core.model.Answer;
import com.skillproof.backend_core.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByQuestionSessionId(Long sessionId);

    Optional<Answer> findByQuestion(Question question);
}