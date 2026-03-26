package com.skillproof.backend_core.repository;



import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skillproof.backend_core.model.Badge;
import com.skillproof.backend_core.model.User;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {

    Optional<Badge> findByVerificationToken(String token);

    Optional<Badge> findBySessionId(Long sessionId);

    List<Badge> findByUserAndIsActiveTrueOrderByIssuedAtDesc(User user);
}
