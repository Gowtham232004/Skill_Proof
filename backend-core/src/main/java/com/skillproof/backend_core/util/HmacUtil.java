package com.skillproof.backend_core.util;


import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HmacUtil {

    @Value("${jwt.secret}")
    private String secret;

    /**
     * Generate a cryptographically signed badge token.
     * Token = HMAC-SHA256(secret, userId + repoName + score + timestamp)
     * Any tampering with badge data invalidates this token.
     */
    public String generateBadgeToken(Long userId, String repoName,
                                      Integer score, long timestamp) {
        String data = userId + ":" + repoName + ":" + score + ":" + timestamp;
        return "sp_" + hmacSha256(data);
    }

    /**
     * Verify a badge token is authentic and untampered.
     */
    public boolean verifyBadgeToken(String token, Long userId, String repoName,
                                     Integer score, long timestamp) {
        String expected = generateBadgeToken(userId, repoName, score, timestamp);
        return expected.equals(token);
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32); // First 32 chars
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC token", e);
        }
    }
}