package com.example.demo.security.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityAuditLogger {

    public void logLoginSuccess(String email, String ipAddress) {
        log.info("SECURITY_AUDIT: login_success email={} ip={} timestamp={}", email, ipAddress, java.time.Instant.now());
    }

    public void logLoginFailure(String email, String ipAddress, String reason) {
        log.warn("SECURITY_AUDIT: login_failure email={} ip={} reason={} timestamp={}", email, ipAddress, reason, java.time.Instant.now());
    }

    public void logAccountLocked(String email, String ipAddress, int failedAttempts) {
        log.warn("SECURITY_AUDIT: account_locked email={} ip={} failed_attempts={} timestamp={}", email, ipAddress, failedAttempts, java.time.Instant.now());
    }

    public void logAccountUnlocked(String email, String ipAddress) {
        log.info("SECURITY_AUDIT: account_unlocked email={} ip={} timestamp={}", email, ipAddress, java.time.Instant.now());
    }

    public void logLogout(String email, String ipAddress) {
        log.info("SECURITY_AUDIT: logout email={} ip={} timestamp={}", email, ipAddress, java.time.Instant.now());
    }

    public void logPasswordChanged(String email, String ipAddress) {
        log.info("SECURITY_AUDIT: password_changed email={} ip={} timestamp={}", email, ipAddress, java.time.Instant.now());
    }

    public void logPasswordResetRequested(String email, String userEmail) {
        log.info("SECURITY_AUDIT: password_reset_requested email={} user_email={} timestamp={}", email, userEmail, java.time.Instant.now());
    }

    public void logPasswordResetCompleted(String email, String ipAddress) {
        log.info("SECURITY_AUDIT: password_reset_completed email={} ip={} timestamp={}", email, ipAddress, java.time.Instant.now());
    }

    public void logTokenRefreshed(String email, String ipAddress) {
        log.info("SECURITY_AUDIT: token_refreshed email={} ip={} timestamp={}", email, ipAddress, java.time.Instant.now());
    }

    public void logTokenRevoked(String email, String ipAddress, String reason) {
        log.warn("SECURITY_AUDIT: token_revoked email={} ip={} reason={} timestamp={}", email, ipAddress, reason, java.time.Instant.now());
    }

    public void logAccessDenied(String email, String ipAddress, String requestedPath) {
        log.warn("SECURITY_AUDIT: access_denied email={} ip={} path={} timestamp={}", email, ipAddress, requestedPath, java.time.Instant.now());
    }

    public void logSuspiciousActivity(String email, String ipAddress, String activity) {
        log.error("SECURITY_AUDIT: suspicious_activity email={} ip={} activity={} timestamp={}", email, ipAddress, activity, java.time.Instant.now());
    }

    public void logMfaEnabled(String email, String method, String ipAddress) {
        log.info("SECURITY_AUDIT: mfa_enabled email={} method={} ip={} timestamp={}", email, method, ipAddress, java.time.Instant.now());
    }

    public void logMfaDisabled(String email, String ipAddress) {
        log.info("SECURITY_AUDIT: mfa_disabled email={} ip={} timestamp={}", email, ipAddress, java.time.Instant.now());
    }

    public void logMfaChallengeSent(String email, String method, String ipAddress) {
        log.info("SECURITY_AUDIT: mfa_challenge_sent email={} method={} ip={} timestamp={}", email, method, ipAddress, java.time.Instant.now());
    }

    public void logMfaSuccess(String email, String ipAddress) {
        log.info("SECURITY_AUDIT: mfa_success email={} ip={} timestamp={}", email, ipAddress, java.time.Instant.now());
    }

    public void logMfaFailure(String email, String ipAddress, String reason) {
        log.warn("SECURITY_AUDIT: mfa_failure email={} ip={} reason={} timestamp={}", email, ipAddress, reason, java.time.Instant.now());
    }
}