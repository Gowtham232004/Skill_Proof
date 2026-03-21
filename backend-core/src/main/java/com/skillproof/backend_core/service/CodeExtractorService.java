package com.skillproof.backend_core.service;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CodeExtractorService {

    // Max characters per file to send to AI — prevents token overflow
    private static final int MAX_FILE_CHARS = 3000;

    // ── Main method ──────────────────────────────────────────────────────────
    // Takes map of {filename -> content}, returns structured summary string
    public String extractCodeSummary(Map<String, String> fileContents,
                                      String primaryLanguage) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== CODE SUMMARY ===\n");
        summary.append("Primary Language: ").append(primaryLanguage).append("\n\n");

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String filename = entry.getKey();
            String content = entry.getValue();

            summary.append("--- FILE: ").append(filename).append(" ---\n");

            // Extract structure based on language
            if (filename.endsWith(".java")) {
                summary.append(extractJavaStructure(filename, content));
            } else if (filename.endsWith(".py")) {
                summary.append(extractPythonStructure(filename, content));
            } else if (filename.endsWith(".ts") || filename.endsWith(".tsx") ||
                       filename.endsWith(".js") || filename.endsWith(".jsx")) {
                summary.append(extractJsStructure(filename, content));
            } else {
                // For other files, just include trimmed content
                summary.append(truncate(content, MAX_FILE_CHARS));
            }

            summary.append("\n\n");
        }

        String result = summary.toString();
        log.info("Code summary generated: {} characters", result.length());
        return result;
    }

    // ── Java extraction ──────────────────────────────────────────────────────
    private String extractJavaStructure(String filename, String content) {
        StringBuilder sb = new StringBuilder();

        // Class/interface declaration
        Pattern classPattern = Pattern.compile(
            "(public|private|protected)?\\s*(abstract|final)?\\s*" +
            "(class|interface|enum|record)\\s+(\\w+).*?\\{",
            Pattern.DOTALL
        );
        Matcher classMatcher = classPattern.matcher(content);
        if (classMatcher.find()) {
            sb.append("Type: ").append(classMatcher.group(3))
              .append(" ").append(classMatcher.group(4)).append("\n");
        }

        // Annotations on the class
        List<String> classAnnotations = extractAnnotations(content, 5);
        if (!classAnnotations.isEmpty()) {
            sb.append("Annotations: ").append(String.join(", ", classAnnotations)).append("\n");
        }

        // Method signatures
        List<String> methods = extractJavaMethods(content);
        if (!methods.isEmpty()) {
            sb.append("Methods:\n");
            methods.forEach(m -> sb.append("  - ").append(m).append("\n"));
        }

        // Injected dependencies
        List<String> dependencies = extractJavaDependencies(content);
        if (!dependencies.isEmpty()) {
            sb.append("Dependencies: ").append(String.join(", ", dependencies)).append("\n");
        }

        // Key code patterns — what we'll ask questions about
        sb.append("Key Code Snippet:\n");
        sb.append(truncate(removeBoilerplate(content), MAX_FILE_CHARS));

        return sb.toString();
    }

    private List<String> extractAnnotations(String content, int maxCount) {
        List<String> annotations = new ArrayList<>();
        Pattern p = Pattern.compile("@(\\w+)");
        Matcher m = p.matcher(content);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find() && seen.size() < maxCount) {
            seen.add(m.group(1));
        }
        return new ArrayList<>(seen);
    }

    private List<String> extractJavaMethods(String content) {
        List<String> methods = new ArrayList<>();
        Pattern p = Pattern.compile(
            "(public|private|protected)\\s+[\\w<>\\[\\]]+\\s+(\\w+)\\s*\\([^)]*\\)"
        );
        Matcher m = p.matcher(content);
        int count = 0;
        while (m.find() && count < 10) {
            String signature = m.group().trim();
            if (!signature.contains("class") && !signature.contains("interface")) {
                methods.add(signature);
                count++;
            }
        }
        return methods;
    }

    private List<String> extractJavaDependencies(String content) {
        List<String> deps = new ArrayList<>();
        Pattern p = Pattern.compile(
            "@(Autowired|Inject)\\s+(?:private|protected)?\\s+[\\w<>]+\\s+(\\w+)"
        );
        Matcher m = p.matcher(content);
        while (m.find()) {
            deps.add(m.group(2));
        }
        // Also find constructor injection
        Pattern constructorP = Pattern.compile(
            "@RequiredArgsConstructor|private final ([\\w<>]+) (\\w+);"
        );
        Matcher cm = constructorP.matcher(content);
        while (cm.find() && cm.group(1) != null) {
            deps.add(cm.group(2));
        }
        return deps;
    }

    // ── Python extraction ────────────────────────────────────────────────────
    private String extractPythonStructure(String filename, String content) {
        StringBuilder sb = new StringBuilder();

        // Class definitions
        Pattern classP = Pattern.compile("^class (\\w+).*:", Pattern.MULTILINE);
        Matcher cm = classP.matcher(content);
        List<String> classes = new ArrayList<>();
        while (cm.find()) classes.add(cm.group(1));
        if (!classes.isEmpty()) {
            sb.append("Classes: ").append(String.join(", ", classes)).append("\n");
        }

        // Function definitions
        Pattern funcP = Pattern.compile("^def (\\w+)\\(([^)]*)\\):", Pattern.MULTILINE);
        Matcher fm = funcP.matcher(content);
        List<String> funcs = new ArrayList<>();
        int count = 0;
        while (fm.find() && count < 10) {
            funcs.add(fm.group(1) + "(" + fm.group(2) + ")");
            count++;
        }
        if (!funcs.isEmpty()) {
            sb.append("Functions:\n");
            funcs.forEach(f -> sb.append("  - ").append(f).append("\n"));
        }

        // Route decorators
        Pattern routeP = Pattern.compile("@(app|router)\\.(get|post|put|delete|patch)\\(\"([^\"]+)\"\\)");
        Matcher rm = routeP.matcher(content);
        List<String> routes = new ArrayList<>();
        while (rm.find()) {
            routes.add(rm.group(2).toUpperCase() + " " + rm.group(3));
        }
        if (!routes.isEmpty()) {
            sb.append("API Routes: ").append(String.join(", ", routes)).append("\n");
        }

        sb.append("Key Code Snippet:\n");
        sb.append(truncate(content, MAX_FILE_CHARS));
        return sb.toString();
    }

    // ── JavaScript/TypeScript extraction ─────────────────────────────────────
    private String extractJsStructure(String filename, String content) {
        StringBuilder sb = new StringBuilder();

        // Function/arrow function definitions
        Pattern funcP = Pattern.compile(
            "(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)|" +
            "(?:export\\s+)?(?:const|let)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?\\("
        );
        Matcher fm = funcP.matcher(content);
        List<String> funcs = new ArrayList<>();
        int count = 0;
        while (fm.find() && count < 10) {
            String name = fm.group(1) != null ? fm.group(1) : fm.group(2);
            if (name != null) { funcs.add(name); count++; }
        }
        if (!funcs.isEmpty()) {
            sb.append("Functions/Components: ").append(String.join(", ", funcs)).append("\n");
        }

        // API route patterns (Express)
        Pattern routeP = Pattern.compile(
            "router\\.(get|post|put|delete)\\s*\\(\\s*['\"]([^'\"]+)['\"]"
        );
        Matcher rm = routeP.matcher(content);
        List<String> routes = new ArrayList<>();
        while (rm.find()) {
            routes.add(rm.group(1).toUpperCase() + " " + rm.group(2));
        }
        if (!routes.isEmpty()) {
            sb.append("Routes: ").append(String.join(", ", routes)).append("\n");
        }

        sb.append("Key Code Snippet:\n");
        sb.append(truncate(content, MAX_FILE_CHARS));
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private String removeBoilerplate(String content) {
        // Remove import statements — not useful for questions
        return content.replaceAll("(?m)^import .*$", "")
                      .replaceAll("(?m)^package .*$", "")
                      .trim();
    }

    private String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n... [truncated]";
    }
}