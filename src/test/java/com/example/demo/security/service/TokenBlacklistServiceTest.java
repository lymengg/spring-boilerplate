package com.example.demo.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should blacklist token with TTL")
    void shouldBlacklistToken() {
        String jti = "test-jti-123";
        Duration remainingLifetime = Duration.ofMinutes(10);

        tokenBlacklistService.blacklistToken(jti, remainingLifetime);

        verify(valueOperations).set("token_blacklist:" + jti, "revoked", remainingLifetime);
    }

    @Test
    @DisplayName("Should return true when token is blacklisted")
    void shouldReturnTrueWhenBlacklisted() {
        String jti = "test-jti-123";
        when(redisTemplate.hasKey("token_blacklist:" + jti)).thenReturn(true);

        boolean result = tokenBlacklistService.isTokenBlacklisted(jti);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when token is not blacklisted")
    void shouldReturnFalseWhenNotBlacklisted() {
        String jti = "test-jti-123";
        when(redisTemplate.hasKey("token_blacklist:" + jti)).thenReturn(false);

        boolean result = tokenBlacklistService.isTokenBlacklisted(jti);

        assertThat(result).isFalse();
    }
}
