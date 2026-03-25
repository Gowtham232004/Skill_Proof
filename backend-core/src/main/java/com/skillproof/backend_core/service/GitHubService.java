

package com.skillproof.backend_core.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.skillproof.backend_core.dto.response.GitHubUserResponse;
import com.skillproof.backend_core.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {

    private final RestTemplate restTemplate;

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    @Value("${github.redirect-uri}")
    private String redirectUri;

    private static final String GITHUB_API = "https://api.github.com";
    private static final String GITHUB_OAUTH = "https://github.com";

    public String buildAuthorizationUrl() {
        return GITHUB_OAUTH + "/login/oauth/authorize" +
               "?client_id=" + clientId +
               "&redirect_uri=" + redirectUri +
               "&scope=read:user,public_repo";
    }

    public String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Map<String, String> body = Map.of(
            "client_id", clientId,
            "client_secret", clientSecret,
            "code", code,
            "redirect_uri", redirectUri
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            log.info("🔐 Exchanging GitHub code for token...");
            log.info("   Client ID: {}", clientId);
            log.info("   Redirect URI: {}", redirectUri);
            
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.postForEntity(
                GITHUB_OAUTH + "/login/oauth/access_token", request, Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            log.info("📝 GitHub response: {}", responseBody);
            
            if (responseBody == null) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GITHUB_NULL_RESPONSE",
                    "GitHub returned null response"
                );
            }
            
            if (responseBody.containsKey("error")) {
                String error = (String) responseBody.get("error");
                String errorDescription = (String) responseBody.getOrDefault("error_description", "");
                log.error("❌ GitHub OAuth error: {} - {}", error, errorDescription);
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GITHUB_OAUTH_ERROR",
                    "GitHub OAuth error: " + error + " - " + errorDescription,
                    Map.of("providerError", error)
                );
            }
            
            if (!responseBody.containsKey("access_token")) {
                log.error("❌ No access_token in GitHub response: {}", responseBody);
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GITHUB_TOKEN_MISSING",
                    "GitHub token exchange failed: no access_token in response"
                );
            }
            
            log.info("✅ Successfully obtained access token");
            return (String) responseBody.get("access_token");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("❌ GitHub token exchange error: {}", e.getMessage());
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "GITHUB_TOKEN_EXCHANGE_FAILED",
                "GitHub token exchange failed",
                Map.of("reason", e.getMessage() == null ? "unknown" : e.getMessage())
            );
        }
    }

    public GitHubUserResponse getUserInfo(String accessToken) {
        HttpEntity<Void> request = new HttpEntity<>(githubHeaders(accessToken));
        ResponseEntity<GitHubUserResponse> response = restTemplate.exchange(
            GITHUB_API + "/user", HttpMethod.GET, request, GitHubUserResponse.class
        );
        return response.getBody();
    }

    public List<Map<String, Object>> getUserRepositories(String accessToken) {
        HttpEntity<Void> request = new HttpEntity<>(githubHeaders(accessToken));
        @SuppressWarnings("unchecked")
        ResponseEntity<List<Map<String, Object>>> response = (ResponseEntity<List<Map<String, Object>>>) (ResponseEntity<?>) restTemplate.exchange(
            GITHUB_API + "/user/repos?sort=updated&per_page=100",
            HttpMethod.GET, request, List.class
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> repos = (List<Map<String, Object>>) response.getBody();
        log.info("Fetched {} repositories for user", repos != null ? repos.size() : 0);
        return repos != null ? repos : Collections.emptyList();
    }

    public List<String> getRepositoryFileTree(String accessToken, String owner, String repo) {
        HttpEntity<Void> request = new HttpEntity<>(githubHeaders(accessToken));

        Map<String, Object> repoInfo = getRepositoryInfo(accessToken, owner, repo);
        String branch = (String) repoInfo.getOrDefault("default_branch", "main");

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
            GITHUB_API + "/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1",
            HttpMethod.GET, request, Map.class
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        if (body == null || !body.containsKey("tree")) return Collections.emptyList();

        List<String> filePaths = new ArrayList<>();
        for (Object item : (List<?>) body.get("tree")) {
            Map<?, ?> node = (Map<?, ?>) item;
            if ("blob".equals(node.get("type"))) filePaths.add((String) node.get("path"));
        }
        log.info("Repo {} has {} files", repo, filePaths.size());
        return filePaths;
    }

    public Map<String, Object> getRepositoryInfo(String accessToken, String owner, String repo) {
        HttpEntity<Void> request = new HttpEntity<>(githubHeaders(accessToken));
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
            GITHUB_API + "/repos/" + owner + "/" + repo, HttpMethod.GET, request, Map.class
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getBody();
        return result;
    }

    public String getFileContent(String accessToken, String owner, String repo, String filePath) {
        try {
            HttpEntity<Void> request = new HttpEntity<>(githubHeaders(accessToken));
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + filePath,
                HttpMethod.GET, request, Map.class
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            if (body == null || !body.containsKey("content")) return "";
            String encoded = ((String) body.get("content")).replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            log.warn("Could not fetch {}: {}", filePath, e.getMessage());
            return "";
        }
    }

    public Map<String, String> fetchFileContents(String accessToken, String owner,
                                                   String repo, List<String> paths) {
        Map<String, String> contents = new LinkedHashMap<>();
        for (String path : paths) {
            String content = getFileContent(accessToken, owner, repo, path);
            if (!content.isBlank()) {
                contents.put(path.substring(path.lastIndexOf('/') + 1), content);
            }
        }
        log.info("Fetched {} files", contents.size());
        return contents;
    }

    private HttpHeaders githubHeaders(String accessToken) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + accessToken);
        h.set("Accept", "application/vnd.github.v3+json");
        return h;
    }
}