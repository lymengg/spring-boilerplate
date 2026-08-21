package com.example.demo.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_PREFIX = "token_blacklist:";

    /**
     * Blacklists an access token by its JTI with TTL matching the token's remaining lifetime.
     * This prevents the token from being used after logout, password change, etc.
     */
    public void blacklistToken(String jti, Duration remainingLifetime) {
        try {
            String key = BLACKLIST_PREFIX + jti;
            redisTemplate.opsForValue().set(key, "revoked", remainingLifetime);
            log.debug("Blacklisted access token with jti: {}", jti);
        } catch (Exception e) {
            log.warn("Failed to blacklist access token with jti {}: {}", jti, e.getMessage());
        }
    }

    /**
     * Checks if an access token has been blacklisted.
     * Returns false if Redis is unavailable (fail-open for availability).
     */
    public boolean isTokenBlacklisted(String jti) {
        try {
            String key = BLACKLIST_PREFIX + jti;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Failed to check blacklist for token jti {}: {}", jti, e.getMessage());
            return false;
        }
    }
}
