package com.skillproof.backend_core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.model.SkillGapReport;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.SkillGapReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GapAnalyzerService {

    private final SkillGapReportRepository gapReportRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void analyzeAsync(User user, VerificationSession session, String codeContent) {
        try {
            log.info("Starting gap analysis for user {} session {}", user.getId(), session.getId());
            List<Map<String, Object>> gaps = runPatternScan(codeContent, session.getRepoLanguage());
            saveReport(user, session, gaps);
            log.info("Gap analysis complete: {} gaps found", gaps.size());
        } catch (Exception e) {
            log.error("Gap analysis failed for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private List<Map<String, Object>> runPatternScan(String code, String language) throws Exception {
        String patternFile = "java".equalsIgnoreCase(language)
            ? "gap-patterns/java-patterns.json"
            : "gap-patterns/java-patterns.json"; // extend for other languages later

        InputStream is = new ClassPathResource(patternFile).getInputStream();
        Map<String, Object> patternConfig = objectMapper.readValue(is, Map.class);
        List<Map<String, Object>> patterns = (List<Map<String, Object>>) patternConfig.get("patterns");

        List<Map<String, Object>> foundGaps = new ArrayList<>();

        for (Map<String, Object> pattern : patterns) {
            String regex = (String) pattern.get("regex");
            boolean negated = Boolean.TRUE.equals(pattern.get("negated"));
            
            try {
                boolean matched = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                                         .matcher(code).find();

                // For negated: gap = pattern NOT found (good thing missing)
                // For non-negated: gap = pattern IS found (bad thing found)
                boolean isGap = negated ? !matched : matched;

                if (isGap) {
                    foundGaps.add(Map.of(
                        "id", pattern.get("id"),
                        "dimension", pattern.get("dimension"),
                        "severity", pattern.get("severity"),
                        "name", pattern.get("name"),
                        "description", pattern.get("description"),
                        "fix", pattern.get("fix"),
                        "resource", pattern.get("resource"),
                        "estimatedHours", pattern.get("estimatedHours")
                    ));
                }
            } catch (Exception e) {
                log.warn("Pattern {} failed to compile: {}", pattern.get("id"), e.getMessage());
            }
        }

        return foundGaps;
    }

    private void saveReport(User user, VerificationSession session, List<Map<String, Object>> gaps) throws Exception {
        long critical = gaps.stream().filter(g -> "CRITICAL".equals(g.get("severity"))).count();
        long important = gaps.stream().filter(g -> "IMPORTANT".equals(g.get("severity"))).count();
        long minor = gaps.stream().filter(g -> "MINOR".equals(g.get("severity"))).count();

        int healthScore = Math.max(0, 100 - (int)(critical * 20 + important * 10 + minor * 5));

        SkillGapReport report = SkillGapReport.builder()
            .user(user)
            .session(session)
            .gapsJson(objectMapper.writeValueAsString(gaps))
            .overallHealthScore(healthScore)
            .criticalCount((int) critical)
            .importantCount((int) important)
            .minorCount((int) minor)
            .build();

        gapReportRepository.save(report);
    }

    public Optional<SkillGapReport> getLatestReport(Long userId) {
        return gapReportRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
    }
}
