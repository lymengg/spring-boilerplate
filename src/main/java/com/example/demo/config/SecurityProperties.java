package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        private long windowMillis = 60_000;
        private List<String> trustedProxies = new ArrayList<>();
        private PerUserLimits perUser = new PerUserLimits();

        @Getter
        @Setter
        public static class PerUserLimits {
            private int forgotPassword = 10;
            private int resetPassword = 10;
            private int mfaVerify = 10;
        }
    }
}
