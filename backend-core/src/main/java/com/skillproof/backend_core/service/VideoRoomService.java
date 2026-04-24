package com.skillproof.backend_core.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.skillproof.backend_core.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoRoomService {

    @Value("${video.provider:auto}")
    private String videoProvider;

    @Value("${video.jitsi-base-url:https://meet.jit.si}")
    private String jitsiBaseUrl;

    @Value("${daily.api-key:}")
    private String dailyApiKey;

    @Value("${daily.base-url:https://api.daily.co/v1}")
    private String dailyBaseUrl;

    private final RestTemplate restTemplate;

    public Map<String, String> createRoom(String roomName) {
        String normalizedRoom = normalizeRoomName(roomName);
        String provider = normalizeProvider(videoProvider);

        if ("jitsi".equals(provider)) {
            return createJitsiRoom(normalizedRoom);
        }

        if ("daily".equals(provider)) {
            return createDailyRoom(normalizedRoom);
        }

        try {
            return createDailyRoom(normalizedRoom);
        } catch (ApiException ex) {
            log.warn("Daily room creation unavailable ({}). Falling back to Jitsi.", ex.getCode());
            return createJitsiRoom(normalizedRoom);
        }
    }

    private Map<String, String> createDailyRoom(String roomName) {
        ensureDailyConfigured();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", roomName);
            body.put("privacy", "public");

            Map<String, Object> properties = new HashMap<>();
            properties.put("exp", System.currentTimeMillis() / 1000 + 3600);
            properties.put("enable_chat", true);
            properties.put("start_video_off", false);
            properties.put("start_audio_off", false);
            body.put("properties", properties);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + dailyApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                dailyBaseUrl + "/rooms",
                entity,
                Map.class
            );

            if (response != null) {
                String url = String.valueOf(response.getOrDefault("url", "")).trim();
                String name = String.valueOf(response.getOrDefault("name", "")).trim();
                if (!url.isBlank()) {
                    return Map.of("url", url, "name", name);
                }
            }
        } catch (RestClientException ex) {
            log.error("Failed to create Daily.co room", ex);
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "VIDEO_ROOM_CREATE_FAILED",
                "Daily video room creation failed. Verify DAILY_API_KEY and Daily subdomain setup."
            );
        }

        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "VIDEO_ROOM_URL_MISSING",
            "Daily video room was created but URL was missing in provider response."
        );
    }

    private Map<String, String> createJitsiRoom(String roomName) {
        String base = safeTrim(jitsiBaseUrl);
        if (base.isBlank()) {
            base = "https://meet.jit.si";
        }
        String url = base.replaceAll("/+$", "") + "/" + roomName;
        return Map.of("url", url, "name", roomName);
    }

    private void ensureDailyConfigured() {
        if (dailyApiKey == null || dailyApiKey.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "DAILY_NOT_CONFIGURED",
                "Daily API key is not configured. Set DAILY_API_KEY and retry."
            );
        }
    }

    private String normalizeProvider(String provider) {
        String value = safeTrim(provider).toLowerCase(Locale.ROOT);
        if ("daily".equals(value) || "jitsi".equals(value)) {
            return value;
        }
        return "auto";
    }

    private String normalizeRoomName(String roomName) {
        String candidate = safeTrim(roomName).toLowerCase(Locale.ROOT);
        if (candidate.isBlank()) {
            candidate = "skillproof-room";
        }
        candidate = candidate.replaceAll("[^a-z0-9_-]", "-");
        candidate = candidate.replaceAll("-+", "-");
        candidate = candidate.replaceAll("^-|-$", "");
        if (candidate.length() > 64) {
            candidate = candidate.substring(0, 64);
        }
        return candidate.isBlank() ? "skillproof-room" : candidate;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
