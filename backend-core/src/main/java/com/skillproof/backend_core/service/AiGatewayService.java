package com.skillproof.backend_core.service;

import java.util.ArrayList;
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
