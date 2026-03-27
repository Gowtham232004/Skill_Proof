package com.skillproof.backend_core.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.skillproof.backend_core.dto.request.CreateChallengeRequest;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DockerExecutionService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("python", "javascript", "java");
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("SP_SUMMARY:(\\d+)/(\\d+)");
    private static final Pattern CASE_PATTERN = Pattern.compile("SP_CASE:(\\d+):(PASS|FAIL|ERROR)");
    private static final Pattern EXPECTED_PATTERN = Pattern.compile("SP_EXPECTED:(\\d+):(.*)");
    private static final Pattern ACTUAL_PATTERN = Pattern.compile("SP_ACTUAL:(\\d+):(.*)");
    private static final Pattern ERROR_PATTERN = Pattern.compile("SP_ERROR:(\\d+):(.*)");
    private static final int MAX_OUTPUT_CHARS = 12000;

    @Value("${challenge.docker.enabled:false}")
    private boolean dockerEnabled;

    @Value("${challenge.docker.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${challenge.docker.memory-limit-mb:256}")
    private int defaultMemoryLimitMb;

    @Value("${challenge.docker.temp-dir:.challenge-runner}")
    private String tempDir;

    public DockerExecutionResult evaluateSubmission(
            String language,
            String submittedCode,
            String referenceSolution,
            List<CreateChallengeRequest.TestCaseInput> testCases) {

        String normalizedLanguage = normalizeLanguage(language);
        if (!SUPPORTED_LANGUAGES.contains(normalizedLanguage)) {
            return new DockerExecutionResult(
                0,
                DockerExecutionStatus.ERROR,
                "Unsupported language for challenge execution",
                "",
                "Supported languages: python, javascript, java",
                List.of()
            );
        }

        if (!dockerEnabled) {
            return new DockerExecutionResult(
                0,
                DockerExecutionStatus.ERROR,
                "Docker challenge execution is disabled. Enable challenge.docker.enabled to run real sandbox tests.",
                "",
                "challenge.docker.enabled=false",
                List.of()
            );
        }

        Path runDir = null;
        try {
            runDir = createRunDirectory(normalizedLanguage);
            List<CreateChallengeRequest.TestCaseInput> safeTests = testCases == null ? List.of() : testCases;
            writeSubmissionFiles(runDir, normalizedLanguage, safe(submittedCode), safeTests);

            CommandResult commandResult = runContainer(runDir, normalizedLanguage, timeoutSeconds);
            return toExecutionResult(commandResult, safeTests);
        } catch (IOException ex) {
            log.error("Failed to execute docker challenge run", ex);
            return new DockerExecutionResult(
                0,
                DockerExecutionStatus.ERROR,
                "Failed to prepare docker challenge execution.",
                "",
                truncate("I/O error: " + ex.getMessage()),
                List.of()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new DockerExecutionResult(
                0,
                DockerExecutionStatus.ERROR,
                "Challenge execution interrupted.",
                "",
                truncate(ex.getMessage()),
                List.of()
            );
        } finally {
            if (runDir != null) {
                cleanupDirectory(runDir);
            }
        }
    }

    private String normalizeLanguage(String language) {
        return safe(language).trim().toLowerCase(Locale.ROOT);
    }

    private Path createRunDirectory(String language) throws IOException {
        Path baseDir = Path.of(tempDir).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
        return Files.createTempDirectory(baseDir, "challenge-" + language + "-");
    }

    private void writeSubmissionFiles(Path runDir,
                                      String language,
                                      String submittedCode,
                                      List<CreateChallengeRequest.TestCaseInput> testCases) throws IOException {
        List<CreateChallengeRequest.TestCaseInput> safeTests = testCases == null ? List.of() : testCases;

        switch (language) {
            case "python" -> {
                Files.writeString(runDir.resolve("solution.py"), submittedCode, StandardCharsets.UTF_8);
                Files.writeString(runDir.resolve("test_runner.py"), buildPythonRunner(safeTests), StandardCharsets.UTF_8);
            }
            case "javascript" -> {
                Files.writeString(runDir.resolve("solution.js"), submittedCode, StandardCharsets.UTF_8);
                Files.writeString(runDir.resolve("test_runner.js"), buildJavaScriptRunner(safeTests), StandardCharsets.UTF_8);
            }
            case "java" -> {
                Files.writeString(runDir.resolve("Solution.java"), submittedCode, StandardCharsets.UTF_8);
                Files.writeString(runDir.resolve("TestRunner.java"), buildJavaRunner(safeTests), StandardCharsets.UTF_8);
            }
            default -> throw new IOException("Unsupported language runner: " + language);
        }
    }

    private CommandResult runContainer(Path runDir,
                                       String language,
                                       int timeoutSecondsValue) throws IOException, InterruptedException {
        String image = dockerImage(language);
        String command = containerCommand(language);
        int memoryLimit = Math.max(64, defaultMemoryLimitMb);

        ProcessBuilder processBuilder = new ProcessBuilder(
            "docker",
            "run",
            "--rm",
            "--network",
            "none",
            "--memory",
            memoryLimit + "m",
            "--cpus",
            "1.0",
            "--pids-limit",
            "128",
            "--read-only",
            "--tmpfs",
            "/tmp:rw,size=64m",
            "-v",
            runDir.toAbsolutePath().toString() + ":/workspace:rw",
            "-w",
            "/workspace",
            image,
            "sh",
            "-lc",
            command
        );

        processBuilder.redirectErrorStream(false);
        Process process = processBuilder.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutThread = streamToBuilder(process.getInputStream(), stdout);
        Thread stderrThread = streamToBuilder(process.getErrorStream(), stderr);

        boolean finished = process.waitFor(Math.max(5, timeoutSecondsValue), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            stdoutThread.join(Duration.ofSeconds(2));
            stderrThread.join(Duration.ofSeconds(2));
            return new CommandResult(-1, truncate(stdout.toString()), truncate(stderr.toString()), true);
        }

        int exitCode = process.exitValue();
        stdoutThread.join(Duration.ofSeconds(2));
        stderrThread.join(Duration.ofSeconds(2));
        return new CommandResult(exitCode, truncate(stdout.toString()), truncate(stderr.toString()), false);
    }

    private DockerExecutionResult toExecutionResult(CommandResult commandResult,
                                                    List<CreateChallengeRequest.TestCaseInput> testCases) {
        List<TestCaseResult> parsedResults = parseTestCaseResults(commandResult.stdout(), testCases);

        if (commandResult.timedOut()) {
            return new DockerExecutionResult(
                0,
                DockerExecutionStatus.ERROR,
                "Execution timed out before hidden tests completed.",
                commandResult.stdout(),
                appendNonEmpty(commandResult.stderr(), "Timed out after " + timeoutSeconds + " seconds."),
                parsedResults
            );
        }

        Matcher matcher = SUMMARY_PATTERN.matcher(commandResult.stdout());
        if (!matcher.find()) {
            return new DockerExecutionResult(
                0,
                DockerExecutionStatus.ERROR,
                "Execution failed before test summary could be produced.",
                commandResult.stdout(),
                appendNonEmpty(commandResult.stderr(), "Exit code: " + commandResult.exitCode()),
                parsedResults
            );
        }

        int passed = Integer.parseInt(matcher.group(1));
        int total = Integer.parseInt(matcher.group(2));
        int score = total <= 0 ? 0 : (int) Math.round((passed * 100.0) / total);

        DockerExecutionStatus status = passed == total
            ? DockerExecutionStatus.PASSED
            : DockerExecutionStatus.FAILED;

        String feedback = status == DockerExecutionStatus.PASSED
            ? "All hidden tests passed."
            : "Passed " + passed + " of " + total + " hidden tests.";

        return new DockerExecutionResult(
            score,
            status,
            feedback,
            commandResult.stdout(),
            commandResult.stderr(),
            parsedResults
        );
    }

    private List<TestCaseResult> parseTestCaseResults(String stdout,
                                                      List<CreateChallengeRequest.TestCaseInput> testCases) {
        Map<Integer, String> statusByCase = new HashMap<>();
        Map<Integer, String> expectedByCase = new HashMap<>();
        Map<Integer, String> actualByCase = new HashMap<>();
        Map<Integer, String> errorByCase = new HashMap<>();
        int maxSeenCase = 0;

        for (String line : safe(stdout).split("\\R")) {
            Matcher caseMatcher = CASE_PATTERN.matcher(line);
            if (caseMatcher.matches()) {
                int caseNumber = Integer.parseInt(caseMatcher.group(1));
                statusByCase.put(caseNumber, caseMatcher.group(2));
                maxSeenCase = Math.max(maxSeenCase, caseNumber);
                continue;
            }

            Matcher expectedMatcher = EXPECTED_PATTERN.matcher(line);
            if (expectedMatcher.matches()) {
                int caseNumber = Integer.parseInt(expectedMatcher.group(1));
                expectedByCase.put(caseNumber, expectedMatcher.group(2));
                maxSeenCase = Math.max(maxSeenCase, caseNumber);
                continue;
            }

            Matcher actualMatcher = ACTUAL_PATTERN.matcher(line);
            if (actualMatcher.matches()) {
                int caseNumber = Integer.parseInt(actualMatcher.group(1));
                actualByCase.put(caseNumber, actualMatcher.group(2));
                maxSeenCase = Math.max(maxSeenCase, caseNumber);
                continue;
            }

            Matcher errorMatcher = ERROR_PATTERN.matcher(line);
            if (errorMatcher.matches()) {
                int caseNumber = Integer.parseInt(errorMatcher.group(1));
                errorByCase.put(caseNumber, errorMatcher.group(2));
                maxSeenCase = Math.max(maxSeenCase, caseNumber);
            }
        }

        int size = Math.max(testCases == null ? 0 : testCases.size(), maxSeenCase);
        List<TestCaseResult> results = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            CreateChallengeRequest.TestCaseInput source = testCases != null && i - 1 < testCases.size()
                ? testCases.get(i - 1)
                : null;

            String expectedOutput = expectedByCase.containsKey(i)
                ? expectedByCase.get(i)
                : source != null ? source.getExpectedOutput() : "";

            results.add(new TestCaseResult(
                i,
                "Test #" + i,
                statusByCase.getOrDefault(i, "UNKNOWN"),
                expectedOutput,
                actualByCase.getOrDefault(i, ""),
                errorByCase.getOrDefault(i, "")
            ));
        }
        return results;
    }

    private String dockerImage(String language) {
        return switch (language) {
            case "python" -> "python:3.11-alpine";
            case "javascript" -> "node:20-alpine";
            case "java" -> "eclipse-temurin:21-jdk-alpine";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private String containerCommand(String language) {
        return switch (language) {
            case "python" -> "python test_runner.py";
            case "javascript" -> "node test_runner.js";
            case "java" -> "javac Solution.java TestRunner.java && java TestRunner";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private Thread streamToBuilder(InputStream inputStream, StringBuilder builder) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append(System.lineSeparator());
                }
            } catch (IOException ex) {
                builder.append("[stream read error] ").append(ex.getMessage()).append(System.lineSeparator());
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private String buildPythonRunner(List<CreateChallengeRequest.TestCaseInput> testCases) {
        StringBuilder sb = new StringBuilder();
        sb.append("def _normalize(value):\n");
        sb.append("    return str(value if value is not None else '').strip().replace('\\r\\n', '\\n')\n\n");
        sb.append("cases = [\n");
        for (CreateChallengeRequest.TestCaseInput testCase : testCases) {
            sb.append("    {'stdin': '")
                .append(escapePython(testCase.getStdin()))
                .append("', 'expected': '")
                .append(escapePython(testCase.getExpectedOutput()))
                .append("'},\n");
        }
        sb.append("]\n\n");
        sb.append("try:\n");
        sb.append("    from solution import solve\n");
        sb.append("except Exception as ex:\n");
        sb.append("    print('SP_FATAL:' + str(ex))\n");
        sb.append("    print(f'SP_SUMMARY:0/{len(cases)}')\n");
        sb.append("    raise\n\n");
        sb.append("passed = 0\n");
        sb.append("for idx, case in enumerate(cases, start=1):\n");
        sb.append("    try:\n");
        sb.append("        actual = _normalize(solve(case.get('stdin', '')))\n");
        sb.append("        expected = _normalize(case.get('expected', ''))\n");
        sb.append("        if actual == expected:\n");
        sb.append("            passed += 1\n");
        sb.append("            print(f'SP_CASE:{idx}:PASS')\n");
        sb.append("        else:\n");
        sb.append("            print(f'SP_CASE:{idx}:FAIL')\n");
        sb.append("            print(f'SP_EXPECTED:{idx}:{expected}')\n");
        sb.append("            print(f'SP_ACTUAL:{idx}:{actual}')\n");
        sb.append("    except Exception as ex:\n");
        sb.append("        print(f'SP_CASE:{idx}:ERROR')\n");
        sb.append("        print(f'SP_ERROR:{idx}:{type(ex).__name__}:{ex}')\n");
        sb.append("print(f'SP_SUMMARY:{passed}/{len(cases)}')\n");
        return sb.toString();
    }

    private String buildJavaScriptRunner(List<CreateChallengeRequest.TestCaseInput> testCases) {
        StringBuilder sb = new StringBuilder();
        sb.append("const normalize = (value) => String(value ?? '').trim().replace(/\\r\\n/g, '\\n');\n");
        sb.append("const cases = [\n");
        for (CreateChallengeRequest.TestCaseInput testCase : testCases) {
            sb.append("  { stdin: '")
                .append(escapeJavaScript(testCase.getStdin()))
                .append("', expected: '")
                .append(escapeJavaScript(testCase.getExpectedOutput()))
                .append("' },\n");
        }
        sb.append("];\n\n");
        sb.append("let solve;\n");
        sb.append("try {\n");
        sb.append("  solve = require('./solution').solve;\n");
        sb.append("} catch (err) {\n");
        sb.append("  console.log('SP_FATAL:' + err.message);\n");
        sb.append("  console.log(`SP_SUMMARY:0/${cases.length}`);\n");
        sb.append("  process.exit(1);\n");
        sb.append("}\n\n");
        sb.append("let passed = 0;\n");
        sb.append("cases.forEach((testCase, index) => {\n");
        sb.append("  const idx = index + 1;\n");
        sb.append("  try {\n");
        sb.append("    const actual = normalize(solve(testCase.stdin));\n");
        sb.append("    const expected = normalize(testCase.expected);\n");
        sb.append("    if (actual === expected) {\n");
        sb.append("      passed += 1;\n");
        sb.append("      console.log(`SP_CASE:${idx}:PASS`);\n");
        sb.append("    } else {\n");
        sb.append("      console.log(`SP_CASE:${idx}:FAIL`);\n");
        sb.append("      console.log(`SP_EXPECTED:${idx}:${expected}`);\n");
        sb.append("      console.log(`SP_ACTUAL:${idx}:${actual}`);\n");
        sb.append("    }\n");
        sb.append("  } catch (err) {\n");
        sb.append("    console.log(`SP_CASE:${idx}:ERROR`);\n");
        sb.append("    console.log(`SP_ERROR:${idx}:${err.message}`);\n");
        sb.append("  }\n");
        sb.append("});\n");
        sb.append("console.log(`SP_SUMMARY:${passed}/${cases.length}`);\n");
        return sb.toString();
    }

    private String buildJavaRunner(List<CreateChallengeRequest.TestCaseInput> testCases) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class TestRunner {\n");
        sb.append("  private static String normalize(String value) {\n");
        sb.append("    if (value == null) return \"\";\n");
        sb.append("    return value.trim().replace(\"\\r\\n\", \"\\n\");\n");
        sb.append("  }\n\n");
        sb.append("  public static void main(String[] args) {\n");
        sb.append("    String[][] cases = new String[][] {\n");
        for (CreateChallengeRequest.TestCaseInput testCase : testCases) {
            sb.append("      {\"")
                .append(escapeJava(testCase.getStdin()))
                .append("\", \"")
                .append(escapeJava(testCase.getExpectedOutput()))
                .append("\"},\n");
        }
        sb.append("    };\n");
        sb.append("    int passed = 0;\n");
        sb.append("    for (int i = 0; i < cases.length; i++) {\n");
        sb.append("      int index = i + 1;\n");
        sb.append("      try {\n");
        sb.append("        String actual = normalize(String.valueOf(Solution.solve(cases[i][0])));\n");
        sb.append("        String expected = normalize(cases[i][1]);\n");
        sb.append("        if (actual.equals(expected)) {\n");
        sb.append("          passed++;\n");
        sb.append("          System.out.println(\"SP_CASE:\" + index + \":PASS\");\n");
        sb.append("        } else {\n");
        sb.append("          System.out.println(\"SP_CASE:\" + index + \":FAIL\");\n");
        sb.append("          System.out.println(\"SP_EXPECTED:\" + index + \":\" + expected);\n");
        sb.append("          System.out.println(\"SP_ACTUAL:\" + index + \":\" + actual);\n");
        sb.append("        }\n");
        sb.append("      } catch (Throwable ex) {\n");
        sb.append("        System.out.println(\"SP_CASE:\" + index + \":ERROR\");\n");
        sb.append("        System.out.println(\"SP_ERROR:\" + index + \":\" + ex.getClass().getSimpleName() + \":\" + ex.getMessage());\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("    System.out.println(\"SP_SUMMARY:\" + passed + \"/\" + cases.length);\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String escapePython(String input) {
        return safe(input)
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", "\\n");
    }

    private String escapeJavaScript(String input) {
        return safe(input)
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", "\\n");
    }

    private String escapeJava(String input) {
        return safe(input)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n");
    }

    private void cleanupDirectory(Path path) {
        try {
            if (Files.notExists(path)) {
                return;
            }
            try (var walk = Files.walk(path)) {
                walk.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ex) {
                            log.warn("Failed to delete challenge temp file: {}", p, ex);
                        }
                    });
            }
        } catch (IOException ex) {
            log.warn("Failed to cleanup challenge temp directory: {}", path, ex);
        }
    }

    private String appendNonEmpty(String base, String suffix) {
        if (base == null || base.isBlank()) {
            return suffix;
        }
        return base + System.lineSeparator() + suffix;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_OUTPUT_CHARS ? text : text.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public enum DockerExecutionStatus {
        PASSED,
        FAILED,
        ERROR
    }

    public record DockerExecutionResult(
        Integer score,
        DockerExecutionStatus status,
        String feedback,
        String stdout,
        String stderr,
        List<TestCaseResult> testCases
    ) {
    }

    public record TestCaseResult(
        Integer caseNumber,
        String name,
        String status,
        String expectedOutput,
        String actualOutput,
        String errorMessage
    ) {
    }

    private record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    }
}
