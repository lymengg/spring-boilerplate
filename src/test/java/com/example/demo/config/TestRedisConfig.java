package com.example.demo.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Provides mock RedisTemplate beans for integration tests.
 * The test profile excludes RedisAutoConfiguration so no real Redis is needed.
 * All Redis-dependent services (TokenBlacklistService, RateLimitingService,
 * MfaServiceImpl, RefreshTokenService) receive this mock, which returns
 * null/false by default — matching the fail-open behavior already coded
 * in those services.
 */
@Configuration
public class TestRedisConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> mock = Mockito.mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        SetOperations<String, String> setOps = Mockito.mock(SetOperations.class);
        Mockito.when(mock.opsForValue()).thenReturn(valueOps);
        Mockito.when(mock.opsForSet()).thenReturn(setOps);
        return mock;
    }
}
