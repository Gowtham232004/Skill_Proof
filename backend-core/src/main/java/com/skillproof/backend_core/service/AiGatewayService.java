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

    public List<Question> generateQuestionsViaAI(VerificationSession session,
                                                   String codeSummary,
                                                   String primaryLanguage,
                                                   List<String> frameworks,
                                                   Map<String, String> fileContents) {

        log.info("Calling AI service to generate questions for session {}", session.getId());

        try {
            // Build request payload for Python service
            Map<String, Object> request = Map.of(
                "session_id", session.getId(),
                "code_summary", codeSummary,
                "primary_language", primaryLanguage,
                "frameworks_detected", frameworks,
                "repo_name", session.getRepoName()
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
            log.info("AI service responded successfully for session {}", session.getId());

            // Parse response and extract questions
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = objectMapper.readValue(response, Map.class);

            if ("success".equals(responseData.get("status"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> questionsData = 
                    (List<Map<String, Object>>) responseData.get("questions");

                List<Question> questions = new ArrayList<>();
                for (Map<String, Object> qData : questionsData) {
                    String fileRef = (String) qData.get("file_reference");
                    
                    questions.add(Question.builder()
                        .session(session)
                        .questionNumber((Integer) qData.get("question_number"))
                        .difficulty(
                            Question.Difficulty.valueOf((String) qData.get("difficulty"))
                        )
                        .fileReference(fileRef)
                        .questionText((String) qData.get("question_text"))
                        .codeContext(fileContents.getOrDefault(fileRef, ""))
                        .build());
                }

                log.info("AI service returned {} questions for session {}", 
                    questions.size(), session.getId());
                return questions;
            }

        } catch (RestClientException e) {
            log.error("AI service HTTP error for session {}: {}", 
                session.getId(), e.getMessage());
        } catch (JsonProcessingException e) {
            log.error("AI service response parsing failed for session {}: {}",
                session.getId(), e.getMessage(), e);
        }

        // Fallback: return null to signal fallback
        log.warn("AI service failed. VerificationService will use fallback questions.");
        return null;
    }
}
