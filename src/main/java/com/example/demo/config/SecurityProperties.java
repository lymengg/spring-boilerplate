package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private AccountLockout accountLockout = new AccountLockout();
    private RateLimiting rateLimiting = new RateLimiting();

    @Getter
    @Setter
    public static class AccountLockout {
        private int maxAttempts = 5;
        private int lockoutDurationMinutes = 15;
    }

    @Getter
    @Setter
    public static class RateLimiting {
        private int maxLoginRequests = 5;
        private long windowMillis = 60_000;
    }
}
