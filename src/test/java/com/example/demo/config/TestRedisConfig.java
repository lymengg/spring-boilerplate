package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Provides an in-memory RedisTemplate fake for integration tests.
 * The test profile excludes RedisAutoConfiguration so no real Redis is needed.
 * All Redis-dependent services (TokenBlacklistService, RateLimitingService,
 * MfaServiceImpl, RefreshTokenService) receive this fake, which stores values
 * with TTL and executes the rate-limit script, so the full auth flows
 * (refresh-token rotation, MFA OTP, blacklisting, rate limiting) work end-to-end.
 */
@Configuration
public class TestRedisConfig {

    @Bean
    @Primary
    public InMemoryRedisTemplate redisTemplate() {
        return new InMemoryRedisTemplate();
    }
}
