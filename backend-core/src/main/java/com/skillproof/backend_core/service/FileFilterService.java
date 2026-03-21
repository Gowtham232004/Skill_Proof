package com.skillproof.backend_core.service;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileFilterService {

    // Directories to completely ignore
    private static final Set<String> IGNORED_DIRS = Set.of(
        "node_modules", ".git", "build", "dist", "target", ".next",
        "out", "__pycache__", ".gradle", "vendor", "coverage",
        ".idea", ".vscode", "bin", "obj", ".mvn"
    );

    // File extensions we can actually analyze
    private static final Set<String> ANALYZABLE_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".ts", ".jsx", ".tsx",
        ".go", ".rb", ".php", ".cs", ".cpp", ".c"
    );

    // File extensions to always skip
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
        ".lock", ".min.js", ".min.css", ".map", ".svg", ".png",
        ".jpg", ".jpeg", ".gif", ".ico", ".pdf", ".zip",
        ".class", ".jar", ".war", ".pyc", ".DS_Store"
    );

    // High-value filename keywords — these files are most important
    private static final List<String> HIGH_VALUE_KEYWORDS = List.of(
        "Controller", "Service", "Repository", "Model", "Entity",
        "Config", "Security", "Auth", "Payment", "Order",
        "User", "Product", "Manager", "Handler", "Processor",
        "router", "routes", "middleware", "models", "views",
        "serializer", "validator", "helper", "utils"
    );

    // ── Main method ──────────────────────────────────────────────────────────
    // Takes raw file list from GitHub API, returns top files sorted by relevance
    public List<String> filterAndRankFiles(List<String> allFilePaths) {
        return allFilePaths.stream()
            .filter(this::shouldIncludeFile)
            .sorted(Comparator.comparingInt(this::scoreFile).reversed())
            .limit(15)  // Top 15 most relevant files
            .collect(Collectors.toList());
    }

    // Should this file be included at all?
    private boolean shouldIncludeFile(String path) {
        // Skip ignored directories
        for (String ignoredDir : IGNORED_DIRS) {
            if (path.contains("/" + ignoredDir + "/") ||
                path.startsWith(ignoredDir + "/")) {
                return false;
            }
        }

        // Skip bad extensions
        for (String ext : SKIP_EXTENSIONS) {
            if (path.toLowerCase().endsWith(ext)) {
                return false;
            }
        }

        // Only include analyzable extensions
        boolean hasGoodExtension = ANALYZABLE_EXTENSIONS.stream()
            .anyMatch(ext -> path.toLowerCase().endsWith(ext));

        if (!hasGoodExtension) {
            return false;
        }

        // Skip test files for question generation
        // (tests reveal implementation details we want devs to explain themselves)
        String lower = path.toLowerCase();
        if (lower.contains("test") || lower.contains("spec") ||
            lower.contains("__tests__") || lower.endsWith("test.js") ||
            lower.endsWith("spec.ts")) {
            return false;
        }

        return true;
    }

    // Score a file 0–100 based on how valuable it is for question generation
    private int scoreFile(String path) {
        int score = 0;
        String filename = path.substring(path.lastIndexOf('/') + 1);

        // High-value keyword in filename = +30 points
        for (String keyword : HIGH_VALUE_KEYWORDS) {
            if (filename.contains(keyword)) {
                score += 30;
                break;
            }
        }

        // Shallow path depth = more important (core files are near root)
        int depth = (int) path.chars().filter(c -> c == '/').count();
        score += Math.max(0, 20 - (depth * 4));

        // Prefer certain languages
        if (path.endsWith(".java")) score += 15;
        else if (path.endsWith(".py")) score += 12;
        else if (path.endsWith(".ts") || path.endsWith(".tsx")) score += 10;
        else if (path.endsWith(".js") || path.endsWith(".jsx")) score += 8;

        // Bonus for main application files
        String lower = filename.toLowerCase();
        if (lower.contains("main") || lower.contains("app") ||
            lower.contains("application") || lower.contains("index")) {
            score += 10;
        }

        return score;
    }

    // Detect primary language from file list
    public String detectPrimaryLanguage(List<String> filePaths) {
        Map<String, Long> languageCounts = new HashMap<>();

        for (String path : filePaths) {
            if (path.endsWith(".java")) languageCounts.merge("Java", 1L, Long::sum);
            else if (path.endsWith(".py")) languageCounts.merge("Python", 1L, Long::sum);
            else if (path.endsWith(".ts") || path.endsWith(".tsx")) languageCounts.merge("TypeScript", 1L, Long::sum);
            else if (path.endsWith(".js") || path.endsWith(".jsx")) languageCounts.merge("JavaScript", 1L, Long::sum);
            else if (path.endsWith(".go")) languageCounts.merge("Go", 1L, Long::sum);
        }

        return languageCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Unknown");
    }

    // Detect frameworks from file contents and filenames
    public List<String> detectFrameworks(List<String> filePaths,
                                         Map<String, String> fileContents) {
        Set<String> frameworks = new LinkedHashSet<>();

        String allContent = String.join("\n", fileContents.values()).toLowerCase();
        List<String> allPaths = filePaths.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toList());

        // Java frameworks
        if (allContent.contains("@springbootapplication") ||
            allContent.contains("spring.boot")) frameworks.add("Spring Boot");
        if (allContent.contains("@restcontroller") ||
            allContent.contains("@controller")) frameworks.add("Spring MVC");
        if (allContent.contains("@entity") ||
            allContent.contains("jparepository")) frameworks.add("Spring Data JPA");
        if (allContent.contains("@enablewebsecurity") ||
            allContent.contains("securityfilterchain")) frameworks.add("Spring Security");

        // Databases
        if (allContent.contains("mysql") ||
            allPaths.stream().anyMatch(p -> p.contains("mysql"))) frameworks.add("MySQL");
        if (allContent.contains("mongodb") ||
            allContent.contains("mongoclient")) frameworks.add("MongoDB");
        if (allContent.contains("postgresql") ||
            allContent.contains("postgres")) frameworks.add("PostgreSQL");
        if (allContent.contains("redis")) frameworks.add("Redis");

        // Auth
        if (allContent.contains("jwt") ||
            allContent.contains("jsonwebtoken")) frameworks.add("JWT");
        if (allContent.contains("oauth2") ||
            allContent.contains("oauth")) frameworks.add("OAuth2");

        // Python frameworks
        if (allContent.contains("fastapi") ||
            allContent.contains("from fastapi")) frameworks.add("FastAPI");
        if (allContent.contains("django")) frameworks.add("Django");
        if (allContent.contains("flask")) frameworks.add("Flask");

        // JS/TS frameworks
        if (allContent.contains("express")) frameworks.add("Express.js");
        if (allContent.contains("nextjs") ||
            allContent.contains("next/app")) frameworks.add("Next.js");
        if (allContent.contains("react")) frameworks.add("React");

        // Docker
        if (allPaths.stream().anyMatch(p -> p.contains("dockerfile") ||
            p.contains("docker-compose"))) frameworks.add("Docker");

        log.info("Detected frameworks: {}", frameworks);
        return new ArrayList<>(frameworks);
    }
}