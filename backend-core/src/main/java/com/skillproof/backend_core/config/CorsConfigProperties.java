package com.skillproof.backend_core.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsConfigProperties {
    private List<String> allowedOrigins;
}