package com.skillproof.backend_core.repository;

import com.skillproof.backend_core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGithubUserId(String githubUserId);

    Optional<User> findByGithubUsername(String githubUsername);

    boolean existsByGithubUserId(String githubUserId);
}