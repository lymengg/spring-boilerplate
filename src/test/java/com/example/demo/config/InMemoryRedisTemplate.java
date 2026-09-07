package com.example.demo.config;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * In-memory RedisTemplate fake for integration tests. Implements exactly the
 * operations the application services use (string values with TTL, sets, and
 * the INCR+PEXPIRE rate-limit script) so the full auth flows — refresh-token
 * rotation, MFA OTP storage, token blacklisting, rate limiting — can be
 * exercised end-to-end without a real Redis.
 */
public class InMemoryRedisTemplate extends RedisTemplate<String, String> {

    private final Map<String, Value> store = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sets = new ConcurrentHashMap<>();
    private final Map<String, Long> counters = new ConcurrentHashMap<>();

    private record Value(String value, long expiresAtMillis) {
        boolean isExpired() {
            return expiresAtMillis > 0 && System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private final ValueOperations<String, String> valueOps;
    private final SetOperations<String, String> setOps;

    public InMemoryRedisTemplate() {
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);

        when(valueOps.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Value v = store.get(key);
            if (v == null) {
                return null;
            }
            if (v.isExpired()) {
                store.remove(key);
                return null;
            }
            return v.value();
        });
        doAnswer(inv -> {
            store.put(inv.getArgument(0), new Value(inv.getArgument(1), 0));
            return null;
        }).when(valueOps).set(anyString(), anyString());
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            Duration ttl = inv.getArgument(2);
            store.put(key, new Value(value, System.currentTimeMillis() + ttl.toMillis()));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        when(setOps.members(anyString())).thenAnswer(inv ->
                sets.getOrDefault(inv.getArgument(0), Set.of()));
        doAnswer(inv -> {
            sets.computeIfAbsent(inv.getArgument(0), k -> ConcurrentHashMap.newKeySet())
                    .add(inv.getArgument(1));
            return null;
        }).when(setOps).add(anyString(), anyString());
        doAnswer(inv -> {
            Set<String> members = sets.get(inv.getArgument(0));
            if (members != null) {
                members.remove(inv.getArgument(1));
            }
            return null;
        }).when(setOps).remove(anyString(), anyString());
    }

    @Override
    public void afterPropertiesSet() {
        // No real connection factory in tests.
    }

    @Override
    public ValueOperations<String, String> opsForValue() {
        return valueOps;
    }

    @Override
    public SetOperations<String, String> opsForSet() {
        return setOps;
    }

    @Override
    public Boolean delete(String key) {
        store.remove(key);
        sets.remove(key);
        counters.remove(key);
        return true;
    }

    @Override
    public Boolean hasKey(String key) {
        Value v = store.get(key);
        if (v != null) {
            if (v.isExpired()) {
                store.remove(key);
                return false;
            }
            return true;
        }
        return sets.containsKey(key);
    }

    @Override
    public Boolean expire(String key, Duration timeout) {
        Value v = store.get(key);
        if (v == null) {
            return false;
        }
        store.put(key, new Value(v.value(), System.currentTimeMillis() + timeout.toMillis()));
        return true;
    }

    @Override
    public Long getExpire(String key, TimeUnit timeUnit) {
        Value v = store.get(key);
        if (v == null || v.expiresAtMillis() == 0) {
            return null;
        }
        long remaining = v.expiresAtMillis() - System.currentTimeMillis();
        return remaining > 0 ? timeUnit.convert(remaining, TimeUnit.MILLISECONDS) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        // Implements the INCR + PEXPIRE semantics of RateLimitingService's Lua script.
        String key = keys.get(0);
        long maxRequests = Long.parseLong((String) args[0]);
        long windowMillis = Long.parseLong((String) args[1]);

        Value v = store.get(key);
        if (v != null && v.isExpired()) {
            store.remove(key);
            counters.remove(key);
        }
        long current = counters.merge(key, 1L, Long::sum);
        if (current == 1) {
            store.put(key, new Value("1", System.currentTimeMillis() + windowMillis));
        }
        return (T) Long.valueOf(current <= maxRequests ? 1 : 0);
    }

    /** Clears all state so tests are isolated from each other. */
    public void clear() {
        store.clear();
        sets.clear();
        counters.clear();
    }
}
