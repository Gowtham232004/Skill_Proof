package com.skillproof.backend_core.config;


import com.skillproof.backend_core.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
 
import java.io.IOException;
import java.util.List;
 
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
 
    private final JwtUtil jwtUtil;
 
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
 
        String authHeader = request.getHeader("Authorization");
 
        // If no auth header or doesn't start with Bearer, skip
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
 
        String token = authHeader.substring(7); // Remove "Bearer " prefix
 
        try {
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.extractUserId(token);
                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractRole(token);
 
                // Create Spring Security auth token
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
 
                // Store username in details for easy access in controllers
                auth.setDetails(username);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            // Invalid token — just don't set auth, request proceeds as unauthenticated
            logger.warn("JWT validation failed: " + e.getMessage());
        }
 
        filterChain.doFilter(request, response);
    }
}