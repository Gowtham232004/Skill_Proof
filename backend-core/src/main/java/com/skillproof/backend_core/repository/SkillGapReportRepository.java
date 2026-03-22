package com.skillproof.backend_core.repository;

import com.skillproof.backend_core.model.SkillGapReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillGapReportRepository extends JpaRepository<SkillGapReport, Long> {
    Optional<SkillGapReport> findTopByUserIdOrderByCreatedAtDesc(Long userId);
    List<SkillGapReport> findByUserIdOrderByCreatedAtDesc(Long userId);
}
