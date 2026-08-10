package com.example.demo.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RateLimitingService {

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    private static final String RATE_LIMIT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return current <= tonumber(ARGV[1]) and 1 or 0
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RateLimitingService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
    }

    public boolean isAllowed(String endpoint, String identifier, int maxRequests, long windowMillis) {
        String key = buildKey(endpoint, identifier);
        Long allowed = redisTemplate.execute(
                rateLimitScript,
                List.of(key),
                String.valueOf(maxRequests),
                String.valueOf(windowMillis)
        );
        return allowed != null && allowed == 1;
    }

    public long getRetryAfterSeconds(String endpoint, String identifier) {
        String key = buildKey(endpoint, identifier);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    private String buildKey(String endpoint, String identifier) {
        return RATE_LIMIT_PREFIX + endpoint + ":" + identifier;
    }
}
