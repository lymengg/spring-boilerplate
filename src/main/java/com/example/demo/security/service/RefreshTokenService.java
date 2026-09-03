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
    /** Tokens that were rotated but remain valid for the reuse grace window
     *  (parallel/multi-tab refresh safety). See JwtConfig.refreshTokenGraceWindow. */
    private static final String GRACE_TOKEN_PREFIX = "refresh_token_grace:";
    private static final String GRACE_TOKEN_SET_PREFIX = "user_refresh_tokens_grace:";

    public void storeRefreshToken(String username, String refreshToken, long expirationMs) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String key = REFRESH_TOKEN_PREFIX + tokenHash;
        String userKey = REFRESH_TOKEN_SET_PREFIX + username;
        
        redisTemplate.opsForValue().set(key, username, Duration.ofMillis(expirationMs));
        redisTemplate.opsForSet().add(userKey, tokenHash);
        redisTemplate.expire(userKey, Duration.ofMillis(expirationMs));
        
        log.debug("Stored refresh token for user: {}", username);
    }

    /**
     * Rotates a refresh token: the new token becomes active, the old token
     * remains valid only for the reuse grace window (so a concurrent refresh
     * from another tab using the same token does not fail immediately).
     */
    public void rotateRefreshToken(String username, String oldToken, String newToken, long expirationMs, long graceMs) {
        String oldHash = tokenHashingService.hashToken(oldToken);
        String newHash = tokenHashingService.hashToken(newToken);

        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + newHash, username, Duration.ofMillis(expirationMs));
        redisTemplate.opsForSet().add(REFRESH_TOKEN_SET_PREFIX + username, newHash);

        redisTemplate.opsForValue().set(GRACE_TOKEN_PREFIX + oldHash, username, Duration.ofMillis(graceMs));
        redisTemplate.opsForSet().add(GRACE_TOKEN_SET_PREFIX + username, oldHash);

        log.debug("Rotated refresh token for user: {}", username);
    }

    public boolean validateRefreshToken(String username, String refreshToken) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String storedUsername = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + tokenHash);
        if (Objects.equals(username, storedUsername)) {
            return true;
        }
        String graceUsername = redisTemplate.opsForValue().get(GRACE_TOKEN_PREFIX + tokenHash);
        return Objects.equals(username, graceUsername);
    }

    public void revokeRefreshToken(String refreshToken) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String key = REFRESH_TOKEN_PREFIX + tokenHash;
        String username = redisTemplate.opsForValue().get(key);
        String graceUsername = redisTemplate.opsForValue().get(GRACE_TOKEN_PREFIX + tokenHash);
        String owner = username != null ? username : graceUsername;

        if (owner != null) {
            redisTemplate.delete(key);
            redisTemplate.delete(GRACE_TOKEN_PREFIX + tokenHash);
            redisTemplate.opsForSet().remove(REFRESH_TOKEN_SET_PREFIX + owner, tokenHash);
            redisTemplate.opsForSet().remove(GRACE_TOKEN_SET_PREFIX + owner, tokenHash);
            log.debug("Revoked refresh token for user: {}", owner);
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

        // Grace-window tokens are tracked separately so revocation is airtight
        // (password change / admin force-logout must kill even rotated tokens).
        String graceUserKey = GRACE_TOKEN_SET_PREFIX + username;
        Set<String> graceHashes = redisTemplate.opsForSet().members(graceUserKey);
        if (graceHashes != null) {
            for (String hash : graceHashes) {
                redisTemplate.delete(GRACE_TOKEN_PREFIX + hash);
            }
            redisTemplate.delete(graceUserKey);
            log.debug("Revoked all grace-window refresh tokens for user: {}", username);
        }
    }

    public void revokeUserRefreshToken(String username, String refreshToken) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        String userKey = REFRESH_TOKEN_SET_PREFIX + username;
        String graceUserKey = GRACE_TOKEN_SET_PREFIX + username;

        redisTemplate.delete(REFRESH_TOKEN_PREFIX + tokenHash);
        redisTemplate.delete(GRACE_TOKEN_PREFIX + tokenHash);
        redisTemplate.opsForSet().remove(userKey, tokenHash);
        redisTemplate.opsForSet().remove(graceUserKey, tokenHash);
        log.debug("Revoked specific refresh token for user: {}", username);
    }

    public boolean isTokenRevoked(String refreshToken) {
        String tokenHash = tokenHashingService.hashToken(refreshToken);
        boolean active = redisTemplate.hasKey(REFRESH_TOKEN_PREFIX + tokenHash);
        boolean inGrace = redisTemplate.hasKey(GRACE_TOKEN_PREFIX + tokenHash);
        return !active && !inGrace;
    }
}