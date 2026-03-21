package com.skillproof.backend_core.dto.response;



import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// Maps the response from GitHub's /user API endpoint
@Data
public class GitHubUserResponse {

    private String id;          // GitHub's numeric user ID (comes as number, stored as String)

    private String login;       // GitHub username e.g. "gowtham123"

    private String name;        // Display name e.g. "Gowtham M S"

    private String email;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String bio;

    @JsonProperty("public_repos")
    private Integer publicRepos;

    private String location;
}