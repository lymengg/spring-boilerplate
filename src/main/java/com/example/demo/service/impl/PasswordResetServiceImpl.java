package com.example.demo.service.impl;

import com.example.demo.config.AppProperties;
import com.example.demo.config.SecurityProperties;
import com.example.demo.dto.ForgotPasswordRequest;
import com.example.demo.dto.ResetPasswordRequest;
import com.example.demo.entity.PasswordResetToken;
import com.example.demo.entity.User;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.service.RateLimitingService;
import com.example.demo.security.service.TokenHashingService;
import com.example.demo.service.EmailService;
import com.example.demo.service.PasswordResetService;
import com.example.demo.service.TokenService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Isolates the self-service password reset flow. Keeping token generation,
 * hashing, and expiration in one place enforces single-use, time-bound reset
 * tokens and keeps email rate limiting consistent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final int TOKEN_EXPIRATION_MINUTES = 15;

    private final UserService userService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TokenHashingService tokenHashingService;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final SecurityProperties securityProperties;
    private final RateLimitingService rateLimitingService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditLogger securityAuditLogger;

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, String ipAddress) {
        int maxRequests = securityProperties.getRateLimiting().getPerUser().getForgotPassword();
        long windowMillis = securityProperties.getRateLimiting().getWindowMillis();
        if (!rateLimitingService.isAllowed("forgot-password", request.getEmail(), maxRequests, windowMillis)) {
            throw new org.springframework.security.authentication.LockedException("Too many requests. Please try again later.");
        }

        userService.findByEmail(request.getEmail()).ifPresent(user -> {
            String rawToken = tokenHashingService.generateSecureToken();
            String tokenHash = tokenHashingService.hashToken(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .tokenHash(tokenHash)
                    .user(user)
                    .expiresAt(Instant.now().plus(TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String resetLink = appProperties.getBaseUrl() + "/api/auth/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

            securityAuditLogger.logPasswordResetRequested(user.getUsername(), user.getEmail());
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ipAddress) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String tokenHash = tokenHashingService.hashToken(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("Reset token has expired or already been used");
        }

        User user = resetToken.getUser();

        int maxRequests = securityProperties.getRateLimiting().getPerUser().getResetPassword();
        long windowMillis = securityProperties.getRateLimiting().getWindowMillis();
        if (!rateLimitingService.isAllowed("reset-password", user.getUsername(), maxRequests, windowMillis)) {
            throw new org.springframework.security.authentication.LockedException("Too many requests. Please try again later.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userService.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        tokenService.revokeAllUserRefreshTokens(user.getUsername());
        securityAuditLogger.logPasswordResetCompleted(user.getUsername(), ipAddress);
    }

    @Override
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = passwordResetTokenRepository.deleteExpiredAndUsed(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired/used password reset tokens", deleted);
        }
    }
}
