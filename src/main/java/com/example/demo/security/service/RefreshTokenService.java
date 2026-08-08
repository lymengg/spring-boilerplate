package com.example.demo.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final TokenHashingService tokenHashingService;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String REFRESH_TOKEN_SET_PREFIX = "user_refresh_tokens:";

    public void storeRefreshToken(String username, String refreshToken, long expirationMs) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String key = REFRESH_TOKEN_PREFIX + tokenHash;
        String userKey = REFRESH_TOKEN_SET_PREFIX + username;
        
        redisTemplate.opsForValue().set(key, username, Duration.ofMillis(expirationMs));
        redisTemplate.opsForSet().add(userKey, tokenHash);
        redisTemplate.expire(userKey, Duration.ofMillis(expirationMs));
        
        log.debug("Stored refresh token for user: {}", username);
    }

    public boolean validateRefreshToken(String username, String refreshToken) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String key = REFRESH_TOKEN_PREFIX + tokenHash;
        String storedUsername = redisTemplate.opsForValue().get(key);
        return Objects.equals(username, storedUsername);
    }

    public void revokeRefreshToken(String refreshToken) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String key = REFRESH_TOKEN_PREFIX + tokenHash;
        String username = redisTemplate.opsForValue().get(key);
        
        if (username != null) {
            String userKey = REFRESH_TOKEN_SET_PREFIX + username;
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(userKey, tokenHash);
            log.debug("Revoked refresh token for user: {}", username);
        }
    }

    public void revokeAllUserRefreshTokens(String username) {
        String userKey = REFRESH_TOKEN_SET_PREFIX + username;
        Set<String> tokenHashes = redisTemplate.opsForSet().members(userKey);
        
        if (tokenHashes != null) {
            for (String hash : tokenHashes) {
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + hash);
            }
            redisTemplate.delete(userKey);
            log.debug("Revoked all refresh tokens for user: {}", username);
        }
    }

    public void revokeUserRefreshToken(String username, String refreshToken) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String userKey = REFRESH_TOKEN_SET_PREFIX + username;
        String tokenKey = REFRESH_TOKEN_PREFIX + tokenHash;
        
        redisTemplate.delete(tokenKey);
        redisTemplate.opsForSet().remove(userKey, tokenHash);
        log.debug("Revoked specific refresh token for user: {}", username);
    }

    public boolean isTokenRevoked(String refreshToken) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String key = REFRESH_TOKEN_PREFIX + tokenHash;
        return !redisTemplate.hasKey(key);
    }
}