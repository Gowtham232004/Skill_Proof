package com.skillproof.backend_core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.model.SkillGapReport;
import com.skillproof.backend_core.service.GapAnalyzerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/gaps")
@RequiredArgsConstructor
public class GapController {

    private final GapAnalyzerService gapAnalyzerService;
    private final ObjectMapper objectMapper;

    @GetMapping("/latest")
    public ResponseEntity<?> getLatestReport(Authentication auth) throws Exception {
        // Extract user ID from authentication
        Object principalObj = auth.getPrincipal();
        Long userId;
        
        if (principalObj instanceof Number) {
            userId = ((Number) principalObj).longValue();
        } else {
            // If it's a UserDetails object, you may need to adjust this
            return ResponseEntity.badRequest().body(Map.of("error", "Could not extract user ID"));
        }

        Optional<SkillGapReport> report = gapAnalyzerService.getLatestReport(userId);

        if (report.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "hasReport", false,
                "message", "No gap analysis found. Complete a verification first."
            ));
        }

        SkillGapReport r = report.get();
        List<Map<String, Object>> gaps = objectMapper.readValue(
            r.getGapsJson(), List.class
        );

        return ResponseEntity.ok(Map.of(
            "hasReport", true,
            "overallHealthScore", r.getOverallHealthScore(),
            "criticalCount", r.getCriticalCount(),
            "importantCount", r.getImportantCount(),
            "minorCount", r.getMinorCount(),
            "gaps", gaps,
            "createdAt", r.getCreatedAt()
        ));
    }
}
