package com.skillproof.backend_core.repository;



import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationSessionRepository extends JpaRepository<VerificationSession, Long> {

    List<VerificationSession> findByUserOrderByStartedAtDesc(User user);

    Optional<VerificationSession> findByIdAndUser(Long id, User user);

    // Count completed verifications for a user this month (for free tier limit)
    long countByUserAndStatus(User user, VerificationSession.Status status);
}