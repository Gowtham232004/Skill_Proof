package com.skillproof.backend_core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skillproof.backend_core.model.Challenge;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    Optional<Challenge> findByIdAndIsActiveTrue(Long id);

    List<Challenge> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
}
