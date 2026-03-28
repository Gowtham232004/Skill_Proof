package com.skillproof.backend_core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.skillproof.backend_core.model.LiveSession;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    Optional<LiveSession> findBySessionCode(String sessionCode);

    List<LiveSession> findByRecruiterId(Long recruiterId);

    List<LiveSession> findByBadgeToken(String badgeToken);

    Optional<LiveSession> findBySessionCodeAndStatus(String sessionCode, LiveSession.LiveSessionStatus status);
}
