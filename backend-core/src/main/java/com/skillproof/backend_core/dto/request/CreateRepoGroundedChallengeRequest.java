package com.skillproof.backend_core.dto.request;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateRepoGroundedChallengeRequest {

    @NotBlank(message = "Badge token is required")
    private String badgeToken;

    @Min(value = 1, message = "Time limit must be at least 1 second")
    @Max(value = 120, message = "Time limit must be at most 120 seconds")
    private Integer timeLimitSeconds;

    @Pattern(
        regexp = "^(REPO_BUG_FIX|REPO_COMPLETION)$",
        message = "challengeType must be REPO_BUG_FIX or REPO_COMPLETION"
    )
    private String challengeType;

    @Pattern(
        regexp = "^(?i)(python|javascript|java)$",
        message = "preferredLanguage must be python, javascript, or java"
    )
    private String preferredLanguage;

    @Pattern(regexp = "^(OPEN|ASSIGNED)$", message = "accessMode must be OPEN or ASSIGNED")
    private String accessMode;

    private List<@Size(min = 1, max = 100, message = "Candidate username must be between 1 and 100 chars") String> assignedCandidateUsernames;

    private LocalDateTime expiresAt;

    public String getBadgeToken() {
        return badgeToken;
    }

    public void setBadgeToken(String badgeToken) {
        this.badgeToken = badgeToken;
    }

    public Integer getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(Integer timeLimitSeconds) {
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public String getChallengeType() {
        return challengeType;
    }

    public void setChallengeType(String challengeType) {
        this.challengeType = challengeType;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }

    public List<String> getAssignedCandidateUsernames() {
        return assignedCandidateUsernames;
    }

    public void setAssignedCandidateUsernames(List<String> assignedCandidateUsernames) {
        this.assignedCandidateUsernames = assignedCandidateUsernames;
    }
}
