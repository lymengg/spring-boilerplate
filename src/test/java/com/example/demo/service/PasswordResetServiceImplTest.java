package com.example.demo.service;

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
import com.example.demo.service.impl.PasswordResetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceImplTest {

    @Mock private UserService userService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private TokenHashingService tokenHashingService;
    @Mock private EmailService emailService;
    @Mock private AppProperties appProperties;
    @Mock private SecurityProperties securityProperties;
    @Mock private RateLimitingService rateLimitingService;
    @Mock private TokenService tokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecurityAuditLogger securityAuditLogger;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .password("old-encoded")
                .build();

        SecurityProperties.RateLimiting rateLimiting = new SecurityProperties.RateLimiting();
        rateLimiting.getPerUser().setForgotPassword(10);
        rateLimiting.getPerUser().setResetPassword(10);
        when(securityProperties.getRateLimiting()).thenReturn(rateLimiting);
        when(appProperties.getBaseUrl()).thenReturn("http://localhost:8080");
    }

    @Test
    @DisplayName("Forgot password is rate limited per email")
    void forgotPasswordIsRateLimited() {
        when(rateLimitingService.isAllowed("forgot-password", "test@example.com", 10, 60_000L))
                .thenReturn(false);

        assertThatThrownBy(() -> passwordResetService.forgotPassword(
                ForgotPasswordRequest.builder().email("test@example.com").build(), "1.2.3.4"))
                .isInstanceOf(LockedException.class);

        verify(userService, never()).findByEmail(any());
    }

    @Test
    @DisplayName("Forgot password with an unknown email is a silent no-op (no user enumeration)")
    void forgotPasswordWithUnknownEmailIsNoOp() {
        when(rateLimitingService.isAllowed("forgot-password", "unknown@example.com", 10, 60_000L))
                .thenReturn(true);
        when(userService.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        passwordResetService.forgotPassword(
                ForgotPasswordRequest.builder().email("unknown@example.com").build(), "1.2.3.4");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    @DisplayName("Forgot password with a known email stores a hashed token and sends the reset link")
    void forgotPasswordWithKnownEmailStoresTokenAndSendsEmail() {
        when(rateLimitingService.isAllowed("forgot-password", "test@example.com", 10, 60_000L))
                .thenReturn(true);
        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenHashingService.generateSecureToken()).thenReturn("raw-token");
        when(tokenHashingService.hashToken("raw-token")).thenReturn("hashed-token");

        passwordResetService.forgotPassword(
                ForgotPasswordRequest.builder().email("test@example.com").build(), "1.2.3.4");

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"),
                eq("http://localhost:8080/api/auth/reset-password?token=raw-token"));
        verify(securityAuditLogger).logPasswordResetRequested("testuser", "test@example.com");
    }

    @Test
    @DisplayName("Reset password rejects mismatched passwords")
    void resetPasswordRejectsMismatch() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("token")
                .newPassword("NewPass123!")
                .confirmPassword("Different123!")
                .build();

        assertThatThrownBy(() -> passwordResetService.resetPassword(request, "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Passwords do not match");

        verify(passwordResetTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("Reset password rejects an unknown token")
    void resetPasswordRejectsUnknownToken() {
        when(tokenHashingService.hashToken("bogus-token")).thenReturn("bogus-hash");
        when(passwordResetTokenRepository.findByTokenHash("bogus-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                ResetPasswordRequest.builder()
                        .token("bogus-token")
                        .newPassword("NewPass123!")
                        .confirmPassword("NewPass123!")
                        .build(),
                "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset token");

        verify(userService, never()).save(any());
    }

    @Test
    @DisplayName("Reset password rejects an expired or already-used token")
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken expired = PasswordResetToken.builder()
                .tokenHash("hashed-token")
                .user(user)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        when(tokenHashingService.hashToken("old-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                ResetPasswordRequest.builder()
                        .token("old-token")
                        .newPassword("NewPass123!")
                        .confirmPassword("NewPass123!")
                        .build(),
                "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reset token has expired or already been used");

        verify(userService, never()).save(any());
    }

    @Test
    @DisplayName("Reset password is rate limited per user")
    void resetPasswordIsRateLimited() {
        PasswordResetToken valid = PasswordResetToken.builder()
                .tokenHash("hashed-token")
                .user(user)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        when(tokenHashingService.hashToken("valid-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(valid));
        when(rateLimitingService.isAllowed("reset-password", "testuser", 10, 60_000L)).thenReturn(false);

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                ResetPasswordRequest.builder()
                        .token("valid-token")
                        .newPassword("NewPass123!")
                        .confirmPassword("NewPass123!")
                        .build(),
                "1.2.3.4"))
                .isInstanceOf(LockedException.class);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Reset password success encodes the new password, marks the token used and revokes sessions")
    void resetPasswordSuccess() {
        PasswordResetToken valid = PasswordResetToken.builder()
                .tokenHash("hashed-token")
                .user(user)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        when(tokenHashingService.hashToken("valid-token")).thenReturn("hashed-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(valid));
        when(rateLimitingService.isAllowed("reset-password", "testuser", 10, 60_000L)).thenReturn(true);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-encoded");

        passwordResetService.resetPassword(
                ResetPasswordRequest.builder()
                        .token("valid-token")
                        .newPassword("NewPass123!")
                        .confirmPassword("NewPass123!")
                        .build(),
                "1.2.3.4");

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        verify(userService).save(user);
        verify(passwordResetTokenRepository).save(valid);
        verify(tokenService).revokeAllUserRefreshTokens("testuser");
        verify(securityAuditLogger).logPasswordResetCompleted("testuser", "1.2.3.4");
    }
}
