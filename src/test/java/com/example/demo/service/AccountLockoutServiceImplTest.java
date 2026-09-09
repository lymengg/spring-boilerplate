package com.example.demo.service;

import com.example.demo.config.SecurityProperties;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.service.impl.AccountLockoutServiceImpl;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountLockoutServiceImplTest {

    @Mock private UserService userService;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityAuditLogger securityAuditLogger;

    @InjectMocks
    private AccountLockoutServiceImpl accountLockoutService;

    private SecurityProperties.AccountLockout accountLockout;

    @BeforeEach
    void setUp() {
        accountLockout = new SecurityProperties.AccountLockout();
        accountLockout.setMaxAttempts(5);
        accountLockout.setLockoutDurationMinutes(15);
        when(securityProperties.getAccountLockout()).thenReturn(accountLockout);
    }

    @Test
    @DisplayName("prepareForLogin unlocks an expired lockout before continuing")
    void prepareForLoginUnlocksExpiredLockout() {
        User user = User.builder()
                .email("testuser@example.com")
                .accountNonLocked(false)
                .accountLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES))
                .failedAttempts(5)
                .build();

        User result = accountLockoutService.prepareForLogin(user, "1.2.3.4");

        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.getFailedAttempts()).isZero();
        verify(userService).save(user);
        verify(securityAuditLogger).logAccountUnlocked("testuser@example.com", "1.2.3.4");
    }

    @Test
    @DisplayName("prepareForLogin throws LockedException when the lockout has not expired")
    void prepareForLoginThrowsWhenLocked() {
        User user = User.builder()
                .email("testuser@example.com")
                .accountNonLocked(false)
                .accountLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES))
                .failedAttempts(5)
                .build();

        assertThatThrownBy(() -> accountLockoutService.prepareForLogin(user, "1.2.3.4"))
                .isInstanceOf(LockedException.class);

        verify(userService, never()).save(any());
        verify(securityAuditLogger).logAccountLocked("testuser@example.com", "1.2.3.4", 5);
    }

    @Test
    @DisplayName("prepareForLogin returns the user untouched when not locked")
    void prepareForLoginReturnsUserWhenNotLocked() {
        User user = User.builder().build();

        User result = accountLockoutService.prepareForLogin(user, "1.2.3.4");

        assertThat(result).isSameAs(user);
        verify(userService, never()).save(any());
    }

    @Test
    @DisplayName("recordFailedLogin locks the account at the max attempts threshold")
    void recordFailedLoginLocksAtThreshold() {
        User user = User.builder()
                .email("testuser@example.com")
                .failedAttempts(4)
                .accountNonLocked(true)
                .build();

        accountLockoutService.recordFailedLogin(user, "1.2.3.4");

        assertThat(user.getFailedAttempts()).isEqualTo(5);
        assertThat(user.isAccountNonLocked()).isFalse();
        assertThat(user.getAccountLockedUntil()).isNotNull();
        verify(userService).save(user);
        verify(securityAuditLogger).logAccountLocked("testuser@example.com", "1.2.3.4", 5);
        verify(securityAuditLogger).logLoginFailure("testuser@example.com", "1.2.3.4", "Bad credentials");
    }

    @Test
    @DisplayName("recordFailedLogin below the threshold only increments the counter")
    void recordFailedLoginBelowThresholdDoesNotLock() {
        User user = User.builder()
                .email("testuser@example.com")
                .failedAttempts(1)
                .accountNonLocked(true)
                .build();

        accountLockoutService.recordFailedLogin(user, "1.2.3.4");

        assertThat(user.getFailedAttempts()).isEqualTo(2);
        assertThat(user.isAccountNonLocked()).isTrue();
        verify(securityAuditLogger, never()).logAccountLocked(any(), any(), anyInt());
    }

    @Test
    @DisplayName("recordSuccessfulLogin resets the lockout counters")
    void recordSuccessfulLoginResetsCounters() {
        User user = User.builder()
                .email("testuser@example.com")
                .failedAttempts(3)
                .accountNonLocked(false)
                .accountLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        accountLockoutService.recordSuccessfulLogin(user, "1.2.3.4");

        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getAccountLockedUntil()).isNull();
        verify(userService).save(user);
    }
}
