package com.skillproof.backend_core.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class CompareCandidatesRequest {

    @NotEmpty(message = "badgeTokens is required")
    @Size(min = 2, max = 5, message = "badgeTokens must contain between 2 and 5 tokens")
    private List<String> badgeTokens;

    public List<String> getBadgeTokens() {
        return badgeTokens;
    }

    public void setBadgeTokens(List<String> badgeTokens) {
        this.badgeTokens = badgeTokens;
    }
}
