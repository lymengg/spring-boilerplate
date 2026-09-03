package com.example.demo.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;

    private RefreshTokenService refreshTokenService;
    private final TokenHashingService tokenHashingService = new TokenHashingService();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        refreshTokenService = new RefreshTokenService(redisTemplate, tokenHashingService);
    }

    @Test
    @DisplayName("Rotate stores the new token as active and the old token only in the grace window")
    void rotateStoresNewActiveAndOldGrace() {
        String oldToken = "old-refresh";
        String newToken = "new-refresh";
        String oldHash = tokenHashingService.hashToken(oldToken);
        String newHash = tokenHashingService.hashToken(newToken);

        refreshTokenService.rotateRefreshToken("user", oldToken, newToken, 604800000L, 60000L);

        verify(valueOperations).set(eq("refresh_token:" + newHash), eq("user"), eq(Duration.ofMillis(604800000L)));
        verify(valueOperations).set(eq("refresh_token_grace:" + oldHash), eq("user"), eq(Duration.ofMillis(60000L)));
        verify(setOperations).add("user_refresh_tokens:user", newHash);
        verify(setOperations).add("user_refresh_tokens_grace:user", oldHash);
    }

    @Test
    @DisplayName("Validate accepts the active token")
    void validateAcceptsActiveToken() {
        String token = "active-token";
        String hash = tokenHashingService.hashToken(token);
        when(valueOperations.get("refresh_token:" + hash)).thenReturn("user");

        boolean valid = refreshTokenService.validateRefreshToken("user", token);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Validate accepts a token inside the grace window after rotation")
    void validateAcceptsGraceToken() {
        String token = "rotated-token";
        String hash = tokenHashingService.hashToken(token);
        when(valueOperations.get("refresh_token:" + hash)).thenReturn(null);
        when(valueOperations.get("refresh_token_grace:" + hash)).thenReturn("user");

        boolean valid = refreshTokenService.validateRefreshToken("user", token);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Validate rejects unknown or wrong-owner tokens")
    void validateRejectsUnknownToken() {
        String token = "unknown-token";
        String hash = tokenHashingService.hashToken(token);
        when(valueOperations.get("refresh_token:" + hash)).thenReturn(null);
        when(valueOperations.get("refresh_token_grace:" + hash)).thenReturn(null);

        boolean valid = refreshTokenService.validateRefreshToken("user", token);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Revoke deletes main and grace keys and both user set entries")
    void revokeCleansMainAndGrace() {
        String token = "token-to-revoke";
        String hash = tokenHashingService.hashToken(token);
        when(valueOperations.get("refresh_token:" + hash)).thenReturn("user");

        refreshTokenService.revokeRefreshToken(token);

        verify(redisTemplate).delete("refresh_token:" + hash);
        verify(redisTemplate).delete("refresh_token_grace:" + hash);
        verify(setOperations).remove("user_refresh_tokens:user", hash);
        verify(setOperations).remove("user_refresh_tokens_grace:user", hash);
    }

    @Test
    @DisplayName("Revoke all deletes both active and grace-window tokens")
    void revokeAllCleansActiveAndGrace() {
        String activeHash = tokenHashingService.hashToken("active");
        String graceHash = tokenHashingService.hashToken("grace");
        when(setOperations.members("user_refresh_tokens:user")).thenReturn(Set.of(activeHash));
        when(setOperations.members("user_refresh_tokens_grace:user")).thenReturn(Set.of(graceHash));

        refreshTokenService.revokeAllUserRefreshTokens("user");

        verify(redisTemplate).delete("refresh_token:" + activeHash);
        verify(redisTemplate).delete("refresh_token_grace:" + graceHash);
        verify(redisTemplate).delete("user_refresh_tokens:user");
        verify(redisTemplate).delete("user_refresh_tokens_grace:user");
    }

    @Test
    @DisplayName("Token in grace window is not considered revoked")
    void graceTokenIsNotRevoked() {
        String token = "grace-token";
        String hash = tokenHashingService.hashToken(token);
        when(redisTemplate.hasKey("refresh_token:" + hash)).thenReturn(false);
        when(redisTemplate.hasKey("refresh_token_grace:" + hash)).thenReturn(true);

        boolean revoked = refreshTokenService.isTokenRevoked(token);

        assertThat(revoked).isFalse();
        verify(redisTemplate, never()).delete(anyString());
    }
}
