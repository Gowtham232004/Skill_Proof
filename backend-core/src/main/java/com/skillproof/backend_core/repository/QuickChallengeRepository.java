package com.skillproof.backend_core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skillproof.backend_core.model.QuickChallenge;

@Repository
public interface QuickChallengeRepository extends JpaRepository<QuickChallenge, Long> {

    Optional<QuickChallenge> findByChallengeToken(String challengeToken);

    List<QuickChallenge> findByBadgeTokenOrderByCreatedAtDesc(String badgeToken);

    List<QuickChallenge> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
}
