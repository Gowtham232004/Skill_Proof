package com.skillproof.backend_core.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillproof.backend_core.exception.ApiException;
import com.skillproof.backend_core.model.Question;
import com.skillproof.backend_core.model.VerificationSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGatewayService {

    @Value("${ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${ai-service.secret:dev-internal-secret-change-me}")
    private String aiServiceSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> evaluateSingleAnswer(String questionText,
                                                    String fileRef,
                                                    String codeContext,
                                                    String answerText) {
        try {
            Map<String, Object> answer = new HashMap<>();
            answer.put("question_id", 1);
            answer.put("question_text", questionText != null ? questionText : "");
            answer.put("file_reference", fileRef != null ? fileRef : "");
            answer.put("code_context", codeContext != null ? codeContext : "");
            answer.put("answer_text", answerText != null ? answerText : "");

            Map<String, Object> request = Map.of(
                "session_id", 0,
                "answers", List.of(answer),
                "primary_language", "Unknown"
            );

            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/evaluate-answers";
            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_EVALUATION_UNAVAILABLE",
                    "AI evaluation service returned an empty response."
                );
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            Object resultsObj = parsed.get("results");
            if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_EVALUATION_UNAVAILABLE",
                    "AI evaluation service returned incomplete results."
                );
            }

            Object first = results.get(0);
            if (!(first instanceof Map<?, ?> firstResult)) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_EVALUATION_UNAVAILABLE",
                    "AI evaluation service returned invalid data format."
                );
            }

            Map<String, Object> normalized = new HashMap<>();
            normalized.put("accuracy_score", firstResult.get("accuracy_score") != null ? firstResult.get("accuracy_score") : 0);
            normalized.put("depth_score", firstResult.get("depth_score") != null ? firstResult.get("depth_score") : 0);
            normalized.put("specificity_score", firstResult.get("specificity_score") != null ? firstResult.get("specificity_score") : 0);
            normalized.put("composite_score", firstResult.get("composite_score") != null ? firstResult.get("composite_score") : 0);
            normalized.put("ai_feedback", firstResult.get("ai_feedback") != null ? firstResult.get("ai_feedback") : "Evaluation unavailable");
            return normalized;
        } catch (RestClientException | JsonProcessingException e) {
            log.error("Single answer evaluation AI call failed", e);
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_UNAVAILABLE",
                "AI evaluation service is currently unavailable. Please try again."
            );
        }
    }

    public Map<String, Object> evaluateAnswers(Long sessionId,
                                            List<Map<String, Object>> answers,
                                            String primaryLanguage) {
        try {
            Map<String, Object> request = Map.of(
                "session_id", sessionId,
                "answers", answers,
                "primary_language", primaryLanguage != null ? primaryLanguage : "Unknown"
            );

            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/evaluate-answers";
            log.info("Calling AI evaluation service for session {}", sessionId);

            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_EVALUATION_UNAVAILABLE",
                    "AI evaluation service returned an empty response.",
                    Map.of("sessionId", sessionId)
                );
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            validateEvaluationResponse(result, sessionId);
            return result;

        } catch (RestClientException | JsonProcessingException e) {
            log.error("Answer evaluation AI call failed for session {}", sessionId, e);
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_UNAVAILABLE",
                "AI evaluation service is currently unavailable. Please try again.",
                Map.of("sessionId", sessionId)
            );
        }
    }

    private void validateEvaluationResponse(Map<String, Object> response, Long sessionId) {
        if (response == null || response.isEmpty()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_UNAVAILABLE",
                "AI evaluation service returned no data.",
                Map.of("sessionId", sessionId)
            );
        }

        Object status = response.get("status");
        if (!(status instanceof String statusText) || !"success".equalsIgnoreCase(statusText)) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_UNAVAILABLE",
                "AI evaluation service did not return a successful result.",
                Map.of("sessionId", sessionId)
            );
        }

        Object results = response.get("results");
        if (!(results instanceof List<?> resultsList) || resultsList.isEmpty()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_EVALUATION_UNAVAILABLE",
                "AI evaluation service returned incomplete results.",
                Map.of("sessionId", sessionId)
            );
        }
    }

    public Map<String, String> generateFollowUp(String originalQuestion,
                                                 String fileRef,
                                                 String codeContext,
                                                 String developerAnswer) {
        try {
            Map<String, Object> request = Map.of(
                "original_question", originalQuestion != null ? originalQuestion : "",
                "file_reference", fileRef != null ? fileRef : "",
                "code_context", codeContext != null ? codeContext : "",
                "developer_answer", developerAnswer != null ? developerAnswer : ""
            );

            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/generate-followup";
            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_FOLLOWUP_UNAVAILABLE",
                    "AI follow-up service returned an empty response."
                );
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            Object status = parsed.get("status");
            if (!(status instanceof String statusText) || !"success".equalsIgnoreCase(statusText)) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_FOLLOWUP_UNAVAILABLE",
                    "AI follow-up service did not return success status."
                );
            }

            String followupQuestion = String.valueOf(parsed.getOrDefault("followup_question", "")).trim();
            String targetsIdentifier = String.valueOf(parsed.getOrDefault("targets_identifier", "")).trim();
            if (followupQuestion.isBlank()) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_FOLLOWUP_UNAVAILABLE",
                    "AI follow-up service returned an invalid follow-up question."
                );
            }

            return Map.of(
                "followupQuestion", followupQuestion,
                "targetsIdentifier", targetsIdentifier
            );
        } catch (RestClientException | JsonProcessingException e) {
            log.error("Follow-up generation AI call failed", e);
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_FOLLOWUP_UNAVAILABLE",
                "AI follow-up service is currently unavailable. Please try again."
            );
        }
    }

    public Map<String, Object> generateReferenceAnswer(String questionText,
                                                        String fileRef,
                                                        String codeContext) {
        try {
            Map<String, Object> request = Map.of(
                "question_text", questionText != null ? questionText : "",
                "file_reference", fileRef != null ? fileRef : "",
                "code_context", codeContext != null ? codeContext : ""
            );

            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/generate-reference-answer";
            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_REFERENCE_ANSWER_UNAVAILABLE",
                    "AI reference answer service returned an empty response."
                );
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            Object status = parsed.get("status");
            if (!(status instanceof String statusText) || !"success".equalsIgnoreCase(statusText)) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_REFERENCE_ANSWER_UNAVAILABLE",
                    "AI reference answer service did not return success status."
                );
            }

            String referenceAnswer = String.valueOf(parsed.getOrDefault("reference_answer", "")).trim();
            Object checkpointsRaw = parsed.get("review_checkpoints");
            List<?> checkpoints = checkpointsRaw instanceof List<?> list ? list : List.of();

            return Map.of(
                "referenceAnswer", referenceAnswer,
                "reviewCheckpoints", checkpoints
            );
        } catch (RestClientException | JsonProcessingException e) {
            log.error("Reference answer AI call failed", e);
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_REFERENCE_ANSWER_UNAVAILABLE",
                "AI reference answer service is currently unavailable. Please try again."
            );
        }
    }

    public String generateSnippetQuestion(String codeSnippet, String fileReference, String language) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("code_snippet", codeSnippet != null ? codeSnippet : "");
            request.put("file_reference", fileReference != null ? fileReference : "");
            request.put("language", language != null ? language : "Unknown");

            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/generate-snippet-question";
            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            Object question = parsed.get("question");
            if (question == null) {
                return null;
            }

            String output = String.valueOf(question).trim();
            return output.isEmpty() ? null : output;
        } catch (RestClientException | JsonProcessingException ex) {
            log.error("Snippet question generation failed", ex);
            return null;
        }
    }

    public Map<String, Object> generateCodeBug(String codeContext,
                                               String fileReference,
                                               String language) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("code_context", codeContext != null ? codeContext : "");
            request.put("file_reference", fileReference != null ? fileReference : "");
            request.put("language", language != null ? language : "JAVA");

            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/generate-code-bug";
            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                return new HashMap<>();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            return parsed;
        } catch (RestClientException | JsonProcessingException ex) {
            log.error("Code bug generation failed", ex);
            return new HashMap<>();
        }
    }

    public Map<String, Object> generateRepoChallengeFromCode(String codeContext,
                                                              String fileReference,
                                                              String language,
                                                              String challengeType) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("code_context", codeContext != null ? codeContext : "");
            request.put("file_reference", fileReference != null ? fileReference : "");
            request.put("language", language != null ? language : "python");
            request.put("challenge_type", challengeType != null ? challengeType : "REPO_BUG_FIX");
            request.put("variation_seed", System.currentTimeMillis());

            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/generate-repo-challenge";
            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                return Map.of();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (RestClientException | JsonProcessingException ex) {
            log.error("Repo challenge generation failed", ex);
            return Map.of();
        }
    }

    public Map<String, Object> evaluatePrReview(String originalCode,
                                                String modifiedCode,
                                                String bugDescription,
                                                String candidateComments) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("original_code", originalCode != null ? originalCode : "");
            request.put("modified_code", modifiedCode != null ? modifiedCode : "");
            request.put("bug_description", bugDescription != null ? bugDescription : "");
            request.put("candidate_comments", candidateComments != null ? candidateComments : "");

            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/evaluate-pr-review";
            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                return Map.of("score", 0, "bugs_identified", 0, "feedback", "Evaluation unavailable");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            return parsed == null
                ? Map.of("score", 0, "bugs_identified", 0, "feedback", "Evaluation unavailable")
                : parsed;
        } catch (RestClientException | JsonProcessingException ex) {
            log.error("PR review evaluation failed", ex);
            return Map.of("score", 0, "bugs_identified", 0, "feedback", "Evaluation failed");
        }
    }

    public List<Question> generateQuestionsViaAI(VerificationSession session,
                                                   String codeSummary,
                                                   String primaryLanguage,
                                                   List<String> frameworks,
                                                   Map<String, String> fileContents,
                                                   int totalQuestions,
                                                   int conceptualQuestions) {

        log.info("Calling AI service to generate questions for session {}", session.getId());

        try {
            // Build request payload for Python service
            Map<String, Object> request = Map.of(
                "session_id", session.getId(),
                "code_summary", codeSummary,
                "primary_language", primaryLanguage,
                "frameworks_detected", frameworks,
                "repo_name", session.getRepoName(),
                "total_questions", totalQuestions,
                "conceptual_questions", conceptualQuestions
            );

            String requestBody = objectMapper.writeValueAsString(request);

            // Call Python AI service
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", aiServiceSecret);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = aiServiceUrl + "/internal/generate-questions";
            log.info("Calling: {}", url);

            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null || response.isBlank()) {
                throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_QUESTION_GENERATION_UNAVAILABLE",
                    "AI question generation service returned an empty response.",
                    Map.of("sessionId", session.getId())
                );
            }
            log.info("AI service responded successfully for session {}", session.getId());

            // Parse response and extract questions
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = objectMapper.readValue(response, Map.class);

            if ("success".equals(responseData.get("status"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> questionsData = 
                    (List<Map<String, Object>>) responseData.get("questions");

                if (questionsData == null || questionsData.isEmpty()) {
                    throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI_QUESTION_GENERATION_UNAVAILABLE",
                        "AI question generation returned no questions.",
                        Map.of("sessionId", session.getId())
                    );
                }

                List<Question> questions = new ArrayList<>();
                for (Map<String, Object> qData : questionsData) {
                    String fileRef = (String) qData.get("file_reference");
                    String rawQuestionType = qData.get("question_type") != null
                        ? String.valueOf(qData.get("question_type"))
                        : "CODE_GROUNDED";
                    Question.QuestionType questionType = parseQuestionType(rawQuestionType);
                    
                    questions.add(Question.builder()
                        .session(session)
                        .questionNumber((Integer) qData.get("question_number"))
                        .difficulty(
                            Question.Difficulty.valueOf((String) qData.get("difficulty"))
                        )
                        .questionType(questionType)
                        .fileReference(fileRef)
                        .questionText((String) qData.get("question_text"))
                        .codeContext(fileContents.getOrDefault(fileRef, ""))
                        .build());
                }

                log.info("AI service returned {} questions for session {}", 
                    questions.size(), session.getId());
                return questions;
            }

            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_QUESTION_GENERATION_UNAVAILABLE",
                "AI question generation service did not return a successful status.",
                Map.of("sessionId", session.getId())
            );

        } catch (RestClientException e) {
            log.error("AI service HTTP error for session {}: {}", 
                session.getId(), e.getMessage());
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_QUESTION_GENERATION_UNAVAILABLE",
                "AI question generation service is currently unavailable. Please try again.",
                Map.of("sessionId", session.getId())
            );
        } catch (JsonProcessingException e) {
            log.error("AI service response parsing failed for session {}: {}",
                session.getId(), e.getMessage(), e);
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_QUESTION_GENERATION_UNAVAILABLE",
                "AI question generation service returned malformed data.",
                Map.of("sessionId", session.getId())
            );
        }
    }

    private Question.QuestionType parseQuestionType(String rawQuestionType) {
        if (rawQuestionType == null || rawQuestionType.isBlank()) {
            return Question.QuestionType.CODE_GROUNDED;
        }

        try {
            return Question.QuestionType.valueOf(rawQuestionType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown question type '{}', defaulting to CODE_GROUNDED", rawQuestionType);
            return Question.QuestionType.CODE_GROUNDED;
        }
    }
}
