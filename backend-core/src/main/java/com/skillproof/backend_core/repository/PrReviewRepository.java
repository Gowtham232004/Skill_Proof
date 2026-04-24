package com.skillproof.backend_core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skillproof.backend_core.model.PrReview;

@Repository
public interface PrReviewRepository extends JpaRepository<PrReview, Long> {

    Optional<PrReview> findByReviewToken(String reviewToken);

    List<PrReview> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);

    List<PrReview> findByBadgeToken(String badgeToken);

    List<PrReview> findByBadgeTokenAndRecruiterIdOrderByCreatedAtDesc(String badgeToken, Long recruiterId);
}
