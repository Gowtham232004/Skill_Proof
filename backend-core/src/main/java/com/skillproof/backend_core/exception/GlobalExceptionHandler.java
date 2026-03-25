package com.skillproof.backend_core.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.skillproof.backend_core.dto.response.ApiErrorResponse;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        ApiErrorResponse payload = ApiErrorResponse.builder()
            .code(ex.getCode())
            .message(ex.getMessage())
            .details(ex.getDetails())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(ex.getStatus()).body(payload);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage,
                (first, second) -> first,
                LinkedHashMap::new
            ));

        String message = fieldErrors.values().stream().findFirst()
            .orElse("Request validation failed");

        ApiErrorResponse payload = ApiErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message(message)
            .details(Map.of("fieldErrors", fieldErrors))
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandledException(Exception ex) {
        log.error("Unhandled API exception", ex);

        ApiErrorResponse payload = ApiErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("Something went wrong. Please try again.")
            .details(Map.of())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(payload);
    }
}
