package com.skillproof.backend_core.config;


import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

   private final CorsConfigProperties corsConfigProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — we use JWT, not sessions
            .csrf(AbstractHttpConfigurer::disable)

            // CORS configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Stateless session — JWT handles auth
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // URL authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token needed
                .requestMatchers(
                    "/api/auth/github",       // Get GitHub auth URL
                    "/api/auth/github/callback", // OAuth callback
                    "/api/badge/**",          // Public badge pages
                    "/api/live/*/status",     // Public live session status
                    "/api/live/*/questions/*", // Candidate question fetch (no code context)
                    "/api/live/*/questions/*/answer", // Candidate answer submit
                    "/actuator/health",       // Health check
                    "/swagger-ui/**",         // API docs
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/challenges/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/quick-challenge/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/quick-challenge/*/submit").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pr-review/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pr-review/*/submit").permitAll()
                // Everything else needs a valid JWT
                .anyRequest().authenticated()
            )

            // Add JWT filter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsConfigProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
