package com.example.demo.service;

import com.example.demo.config.SecurityProperties;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the account lockout policy and its state transitions. Keeping this
 * separate from login logic makes the security rule explicit, auditable, and
 * easy to tune without touching the authentication flow.
 */
@Service
@RequiredArgsConstructor
public class AccountLockoutService {

    private final UserService userService;
    private final SecurityProperties securityProperties;
    private final SecurityAuditLogger securityAuditLogger;

    /**
     * Clears expired lockouts before enforcing the current lock state, preventing
     * stale lockouts from silently blocking a user.
     */
    @Transactional
    public User prepareForLogin(User user, String ipAddress) {
        if (user.unlockIfExpired()) {
            userService.save(user);
            securityAuditLogger.logAccountUnlocked(user.getUsername(), ipAddress);
        }

        if (!user.isAccountNonLocked()) {
            securityAuditLogger.logAccountLocked(user.getUsername(), ipAddress, user.getFailedAttempts());
            throw new LockedException("Account is locked due to too many failed attempts. Try again later.");
        }

        return user;
    }

    /**
     * Increments failed attempts and applies lockout in one place, centralizing
     * the brute-force protection rule instead of spreading it across services.
     */
    @Transactional
    public void recordFailedLogin(User user, String ipAddress) {
        user.incrementFailedAttempts();

        SecurityProperties.AccountLockout accountLockout = securityProperties.getAccountLockout();
        if (user.getFailedAttempts() >= accountLockout.getMaxAttempts()) {
            user.lockAccount(accountLockout.getLockoutDurationMinutes());
            securityAuditLogger.logAccountLocked(user.getUsername(), ipAddress, user.getFailedAttempts());
        }

        userService.save(user);
        securityAuditLogger.logLoginFailure(user.getUsername(), ipAddress, "Bad credentials");
    }

    /**
     * Resets counters on a successful login so the lockout state stays consistent
     * with the user's actual authentication history.
     */
    @Transactional
    public void recordSuccessfulLogin(User user, String ipAddress) {
        user.unlockAccount();
        userService.save(user);
    }
}
