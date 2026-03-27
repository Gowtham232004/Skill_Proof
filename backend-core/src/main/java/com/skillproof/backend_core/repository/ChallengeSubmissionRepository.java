package com.skillproof.backend_core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skillproof.backend_core.model.ChallengeSubmission;

@Repository
public interface ChallengeSubmissionRepository extends JpaRepository<ChallengeSubmission, Long> {

    List<ChallengeSubmission> findByChallengeIdOrderByCreatedAtDesc(Long challengeId);

    List<ChallengeSubmission> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
