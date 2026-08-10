package com.example.demo.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class RateLimitingServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        rateLimitingService = new RateLimitingService(redisTemplate);
    }

    @Test
    @DisplayName("Should execute atomic Redis rate limit script")
    void shouldExecuteAtomicRateLimitScript() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of("rate_limit:login:127.0.0.1")), anyString(), anyString()))
                .thenReturn(1L);

        boolean allowed = rateLimitingService.isAllowed("login", "127.0.0.1", 5, 60_000);

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("Should return false when script returns 0")
    void shouldReturnFalseWhenOverLimit() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), anyString(), anyString()))
                .thenReturn(0L);

        boolean allowed = rateLimitingService.isAllowed("login", "127.0.0.1", 5, 60_000);

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("Should return retry after TTL in seconds")
    void shouldReturnRetryAfterTtl() {
        when(redisTemplate.getExpire("rate_limit:login:127.0.0.1", TimeUnit.SECONDS))
                .thenReturn(45L);

        long retryAfter = rateLimitingService.getRetryAfterSeconds("login", "127.0.0.1");

        assertThat(retryAfter).isEqualTo(45L);
    }

    @Test
    @DisplayName("Should return zero retry after when TTL is negative")
    void shouldReturnZeroForNegativeTtl() {
        when(redisTemplate.getExpire("rate_limit:login:127.0.0.1", TimeUnit.SECONDS))
                .thenReturn(-2L);

        long retryAfter = rateLimitingService.getRetryAfterSeconds("login", "127.0.0.1");

        assertThat(retryAfter).isZero();
    }
}
