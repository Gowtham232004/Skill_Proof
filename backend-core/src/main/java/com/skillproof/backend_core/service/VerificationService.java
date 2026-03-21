package com.skillproof.backend_core.service;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.dto.request.StartVerificationRequest;
import com.skillproof.backend_core.dto.response.QuestionDto;
import com.skillproof.backend_core.dto.response.VerificationStartResponse;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.User;
import com.skillproof.backend_core.model.VerificationSession;
import com.skillproof.backend_core.repository.QuestionRepository;
import com.skillproof.backend_core.repository.UserRepository;
import com.skillproof.backend_core.repository.VerificationSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final GitHubService gitHubService;
    private final FileFilterService fileFilterService;
    private final CodeExtractorService codeExtractorService;
    private final UserRepository userRepository;
    private final VerificationSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final AiGatewayService aiGatewayService;

    @Value("${testing.bypass-verification-limit:false}")
    private boolean bypassVerificationLimit;

    // Free plan limit
    private static final int FREE_MONTHLY_LIMIT = 3;

    @Transactional
    public VerificationStartResponse startVerification(Long userId,
                                                        StartVerificationRequest req) {

        // 1. Load user and check free tier limit
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Skip limit check if testing mode is enabled
        if (!bypassVerificationLimit && user.getPlan() == User.Plan.FREE) {
            long completedThisMonth = sessionRepository
                .countByUserAndStatus(user, VerificationSession.Status.COMPLETED);
            if (completedThisMonth >= FREE_MONTHLY_LIMIT) {
                throw new RuntimeException(
                    "Free plan limit reached (" + FREE_MONTHLY_LIMIT +
                    " verifications/month). Please upgrade to Pro.");
            }
        }

        if (bypassVerificationLimit && user.getPlan() == User.Plan.FREE) {
            log.info("⚠️  TEST MODE: Bypassing verification limit for user {}", user.getGithubUsername());
        }

        log.info("Starting verification for user {} on repo {}/{}",
            user.getGithubUsername(), req.getRepoOwner(), req.getRepoName());

        // 2. Get repo metadata from GitHub
        Map<String, Object> repoInfo = gitHubService.getRepositoryInfo(
            user.getGithubAccessToken(), req.getRepoOwner(), req.getRepoName()
        );
        String description = (String) repoInfo.getOrDefault("description", "");
        String language    = (String) repoInfo.getOrDefault("language", "Unknown");

        // 3. Fetch full file tree
        List<String> allFiles = gitHubService.getRepositoryFileTree(
            user.getGithubAccessToken(), req.getRepoOwner(), req.getRepoName()
        );

        // 4. Filter to relevant source files only
        List<String> relevantFiles = fileFilterService.filterAndRankFiles(allFiles);
        log.info("Filtered to {} relevant files from {}", relevantFiles.size(), allFiles.size());

        // 5. Fetch file contents
        Map<String, String> fileContents = gitHubService.fetchFileContents(
            user.getGithubAccessToken(), req.getRepoOwner(), req.getRepoName(), relevantFiles
        );

        // 6. Detect frameworks
        List<String> frameworks = fileFilterService.detectFrameworks(
            new ArrayList<>(fileContents.keySet()), fileContents
        );
        String primaryLanguage = fileFilterService.detectPrimaryLanguage(allFiles);

        // 7. Extract code structure summary
        String codeSummary = codeExtractorService.extractCodeSummary(
            fileContents, primaryLanguage
        );

        // 8. Save verification session to DB
        VerificationSession session = VerificationSession.builder()
            .user(user)
            .repoOwner(req.getRepoOwner())
            .repoName(req.getRepoName())
            .repoDescription(description)
            .repoLanguage(language)
            .frameworksDetected(toJson(frameworks))
            .filesAnalyzed(fileContents.size())
            .codeSummary(codeSummary)
            .status(VerificationSession.Status.IN_PROGRESS)
            .build();
        session = sessionRepository.save(session);

        log.info("Session {} created. Code summary: {} chars. Now calling AI service...",
            session.getId(), codeSummary.length());

        // 9. Call AI service to generate questions
        List<Question> questions = aiGatewayService.generateQuestionsViaAI(
            session, 
            codeSummary, 
            primaryLanguage, 
            frameworks, 
            fileContents);

        // If AI service fails, fall back to placeholder questions
        if (questions == null) {
            log.warn("AI service failed, using fallback placeholder questions");
            questions = createPlaceholderQuestions(session, fileContents);
        }

        questionRepository.saveAll(questions);

        // 10. Build response
        return VerificationStartResponse.builder()
            .sessionId(session.getId())
            .repoName(req.getRepoName())
            .repoOwner(req.getRepoOwner())
            .repoDescription(description)
            .primaryLanguage(primaryLanguage)
            .frameworksDetected(frameworks)
            .filesAnalyzed(fileContents.size())
            .questions(questions.stream().map(this::toQuestionDto).collect(Collectors.toList()))
            .status("IN_PROGRESS")
            .build();
    }

    // Temporary placeholder until AI service is ready (Day 3)
    private List<Question> createPlaceholderQuestions(VerificationSession session,
                                                        Map<String, String> fileContents) {
        List<String> fileNames = new ArrayList<>(fileContents.keySet());
        List<Question> questions = new ArrayList<>();

        String[] templates = {
            "Explain the overall architecture of this project and the main responsibilities of each component.",
            "What design patterns have you used in this project and why?",
            "How does authentication and authorization work in this application?",
            "What would happen if the database connection fails? How does your code handle this?",
            "What are the main limitations of your current implementation and how would you improve it?"
        };

        Question.Difficulty[] difficulties = {
            Question.Difficulty.EASY, Question.Difficulty.EASY,
            Question.Difficulty.MEDIUM, Question.Difficulty.HARD, Question.Difficulty.HARD
        };

        for (int i = 0; i < 5; i++) {
            String fileRef = fileNames.size() > i ? fileNames.get(i) : "project";
            questions.add(Question.builder()
                .session(session)
                .questionNumber(i + 1)
                .difficulty(difficulties[i])
                .fileReference(fileRef)
                .questionText(templates[i])
                .codeContext(fileContents.getOrDefault(fileRef, ""))
                .build());
        }
        return questions;
    }

    private QuestionDto toQuestionDto(Question q) {
        return QuestionDto.builder()
            .id(q.getId())
            .questionNumber(q.getQuestionNumber())
            .difficulty(q.getDifficulty().name())
            .fileReference(q.getFileReference())
            .questionText(q.getQuestionText())
            .build();
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}