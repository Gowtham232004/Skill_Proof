package com.skillproof.backend_core.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final Pattern FATAL_PATTERN = Pattern.compile("SP_FATAL:(.*)");
    private static final String DEFAULT_TEMP_SUBDIR = "skillproof-submissions";
    private static final int MAX_OUTPUT_CHARS = 12000;

    @Value("${challenge.docker.enabled:true}")
    private boolean dockerEnabled;

    @Value("${challenge.docker.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${challenge.docker.memory-limit-mb:256}")
    private int defaultMemoryLimitMb;

    @Value("${challenge.docker.temp-dir:/tmp/skillproof-submissions}")
    private String tempDir;

    @Value("${challenge.docker.command:docker}")
    private String dockerCommand;

    @Value("${challenge.docker.host:}")
    private String dockerHost;

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
        Path baseDir = resolveBaseTempDir();
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
                Files.writeString(runDir.resolve("run_java.sh"), buildJavaShellRunner(safeTests.size()), StandardCharsets.UTF_8);
            }
            default -> throw new IOException("Unsupported language runner: " + language);
        }
    }

    private CommandResult runContainer(Path runDir,
                                       String language,
                                       int timeoutSecondsValue) throws IOException, InterruptedException {
        String image = dockerImage(language);
        String command = containerCommand(language);
        String mountSource = toDockerMountSource(runDir.toAbsolutePath().normalize());
        int memoryLimit = Math.max(64, defaultMemoryLimitMb);

        List<String> dockerCommand = List.of(
            this.dockerCommand,
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
            mountSource + ":/workspace:rw",
            "-w",
            "/workspace",
            image,
            "sh",
            "-lc",
            command
        );

        ProcessBuilder processBuilder = new ProcessBuilder(dockerCommand);
        log.debug("Running docker command: {}", formatCommand(dockerCommand));
        log.debug("Docker run directory: {}", runDir.toAbsolutePath());
        log.debug("Configured docker host: {}", safe(dockerHost));

        if (!safe(dockerHost).trim().isBlank()) {
            processBuilder.environment().put("DOCKER_HOST", dockerHost.trim());
        }

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
            return new CommandResult(
                -1,
                truncate(stdout.toString()),
                truncate(stderr.toString()),
                true,
                formatCommand(dockerCommand)
            );
        }

        int exitCode = process.exitValue();
        stdoutThread.join(Duration.ofSeconds(2));
        stderrThread.join(Duration.ofSeconds(2));
        CommandResult result = new CommandResult(
            exitCode,
            truncate(stdout.toString()),
            truncate(stderr.toString()),
            false,
            formatCommand(dockerCommand)
        );

        if (shouldRetryWithWindowsPipe(result)) {
            log.warn("Retrying docker run with Windows default docker host pipe.");
            return rerunWithWindowsPipe(dockerCommand);
        }

        return result;
    }

    private boolean shouldRetryWithWindowsPipe(CommandResult result) {
        if (!isWindows() || result == null) {
            return false;
        }
        if (!safe(dockerHost).trim().isBlank()) {
            return false;
        }
        String stderr = safe(result.stderr()).toLowerCase(Locale.ROOT);
        return stderr.contains("dockerdesktoplinuxengine") || stderr.contains("error during connect");
    }

    private CommandResult rerunWithWindowsPipe(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder retryBuilder = new ProcessBuilder(command);
        retryBuilder.environment().put("DOCKER_HOST", "npipe:////./pipe/docker_engine");
        retryBuilder.redirectErrorStream(false);

        Process retryProcess = retryBuilder.start();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread stdoutThread = streamToBuilder(retryProcess.getInputStream(), stdout);
        Thread stderrThread = streamToBuilder(retryProcess.getErrorStream(), stderr);

        boolean finished = retryProcess.waitFor(Math.max(5, timeoutSeconds), TimeUnit.SECONDS);
        if (!finished) {
            retryProcess.destroyForcibly();
            stdoutThread.join(Duration.ofSeconds(2));
            stderrThread.join(Duration.ofSeconds(2));
            return new CommandResult(-1, truncate(stdout.toString()), truncate(stderr.toString()), true, formatCommand(command));
        }

        int exitCode = retryProcess.exitValue();
        stdoutThread.join(Duration.ofSeconds(2));
        stderrThread.join(Duration.ofSeconds(2));
        return new CommandResult(exitCode, truncate(stdout.toString()), truncate(stderr.toString()), false, formatCommand(command));
    }

    private DockerExecutionResult toExecutionResult(CommandResult commandResult,
                                                    List<CreateChallengeRequest.TestCaseInput> testCases) {
        List<TestCaseResult> parsedResults = parseTestCaseResults(commandResult.stdout(), testCases);
        String fatalError = parseFatalError(commandResult.stdout());

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

        if (fatalError != null) {
            return new DockerExecutionResult(
                0,
                DockerExecutionStatus.ERROR,
                "Execution failed before hidden tests could run.",
                commandResult.stdout(),
                appendNonEmpty(commandResult.stderr(), fatalError),
                parsedResults
            );
        }

        Matcher matcher = SUMMARY_PATTERN.matcher(commandResult.stdout());
        if (!matcher.find()) {
            String diagnostics = buildExecutionDiagnostics(commandResult);
            return new DockerExecutionResult(
                0,
                DockerExecutionStatus.ERROR,
                "Execution failed before summary markers were produced.",
                commandResult.stdout(),
                appendNonEmpty(commandResult.stderr(), diagnostics),
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

    private String parseFatalError(String stdout) {
        Matcher matcher = FATAL_PATTERN.matcher(safe(stdout));
        if (!matcher.find()) {
            return null;
        }
        return "Runner setup error: " + matcher.group(1);
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
                statusByCase.getOrDefault(i, "ERROR"),
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
            case "java" -> "eclipse-temurin:21-jdk";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private String containerCommand(String language) {
        return switch (language) {
            case "python" -> "python test_runner.py";
            case "javascript" -> "node test_runner.js";
            case "java" -> "sh run_java.sh";
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
        sb.append("import sys\n\n");
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
        sb.append("    if not callable(solve):\n");
        sb.append("        raise TypeError('solve is not callable')\n");
        sb.append("except Exception as ex:\n");
        sb.append("    message = f'{type(ex).__name__}:{ex}'\n");
        sb.append("    print('SP_FATAL:' + message)\n");
        sb.append("    for idx in range(1, len(cases) + 1):\n");
        sb.append("        print(f'SP_CASE:{idx}:ERROR')\n");
        sb.append("        print(f'SP_ERROR:{idx}:{message}')\n");
        sb.append("    print(f'SP_SUMMARY:0/{len(cases)}')\n");
        sb.append("    sys.exit(1)\n\n");
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

    private String buildJavaShellRunner(int caseCount) {
        int totalCases = Math.max(0, caseCount);
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("set +e\n");
        sb.append("javac Solution.java TestRunner.java 2> compile.err\n");
        sb.append("if [ $? -ne 0 ]; then\n");
        sb.append("  msg=$(head -n 1 compile.err | tr '\\r' ' ')\n");
        sb.append("  if [ -z \"$msg\" ]; then msg='javac compilation failed'; fi\n");
        sb.append("  echo \"SP_FATAL:${msg}\"\n");
        sb.append("  i=1\n");
        sb.append("  while [ $i -le ").append(totalCases).append(" ]; do\n");
        sb.append("    echo \"SP_CASE:${i}:ERROR\"\n");
        sb.append("    echo \"SP_ERROR:${i}:CompileError:${msg}\"\n");
        sb.append("    i=$((i+1))\n");
        sb.append("  done\n");
        sb.append("  echo \"SP_SUMMARY:0/").append(totalCases).append("\"\n");
        sb.append("  cat compile.err 1>&2\n");
        sb.append("  exit 1\n");
        sb.append("fi\n");
        sb.append("java TestRunner\n");
        sb.append("rc=$?\n");
        sb.append("if [ $rc -ne 0 ]; then\n");
        sb.append("  msg='java runtime failed before summary markers'\n");
        sb.append("  echo \"SP_FATAL:${msg}\"\n");
        sb.append("  i=1\n");
        sb.append("  while [ $i -le ").append(totalCases).append(" ]; do\n");
        sb.append("    echo \"SP_CASE:${i}:ERROR\"\n");
        sb.append("    echo \"SP_ERROR:${i}:RuntimeError:${msg}\"\n");
        sb.append("    i=$((i+1))\n");
        sb.append("  done\n");
        sb.append("  echo \"SP_SUMMARY:0/").append(totalCases).append("\"\n");
        sb.append("fi\n");
        sb.append("exit $rc\n");
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

    private Path resolveBaseTempDir() {
        String configured = safe(tempDir).trim();
        if (configured.isBlank()) {
            return Paths.get(System.getProperty("java.io.tmpdir"), DEFAULT_TEMP_SUBDIR).toAbsolutePath().normalize();
        }

        if (isWindows() && configured.startsWith("/tmp")) {
            return Paths.get(System.getProperty("java.io.tmpdir"), DEFAULT_TEMP_SUBDIR).toAbsolutePath().normalize();
        }

        return Path.of(configured).toAbsolutePath().normalize();
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win");
    }

    private String toDockerMountSource(Path absolutePath) {
        String normalized = absolutePath.toString().replace('\\', '/');
        if (!isWindows()) {
            return normalized;
        }

        if (normalized.length() >= 3 && Character.isLetter(normalized.charAt(0))
                && normalized.charAt(1) == ':' && normalized.charAt(2) == '/') {
            char drive = Character.toLowerCase(normalized.charAt(0));
            return "/" + drive + normalized.substring(2);
        }
        return normalized;
    }

    private String buildExecutionDiagnostics(CommandResult commandResult) {
        String stderr = safe(commandResult.stderr()).toLowerCase(Locale.ROOT);
        String hint = "Exit code: " + commandResult.exitCode() + ". Docker command: " + commandResult.commandSummary();

        if (stderr.contains("cannot connect to the docker daemon")
                || stderr.contains("error during connect")
                || stderr.contains("is the docker daemon running")) {
            return hint + " | Docker daemon is not reachable from the backend process. On Windows set challenge.docker.host=npipe:////./pipe/docker_engine and ensure Docker Desktop is running.";
        }

        if (stderr.contains("invalid mode") || stderr.contains("invalid volume")
                || stderr.contains("mount") || stderr.contains("bind source path does not exist")) {
            return hint + " | Docker volume mount looks invalid for this host path.";
        }

        if (stderr.contains("permission denied") || stderr.contains("access is denied")) {
            return hint + " | Docker process lacks permission to read the challenge temp directory.";
        }

        return hint;
    }

    private String formatCommand(List<String> command) {
        return String.join(" ", command);
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

    private record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut, String commandSummary) {
    }
}
