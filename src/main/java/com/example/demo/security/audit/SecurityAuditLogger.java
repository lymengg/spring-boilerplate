package com.example.demo.security.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityAuditLogger {

    public void logLoginSuccess(String username, String ipAddress) {
        log.info("SECURITY_AUDIT: login_success username={} ip={} timestamp={}", username, ipAddress, java.time.Instant.now());
    }

    public void logLoginFailure(String username, String ipAddress, String reason) {
        log.warn("SECURITY_AUDIT: login_failure username={} ip={} reason={} timestamp={}", username, ipAddress, reason, java.time.Instant.now());
    }

    public void logAccountLocked(String username, String ipAddress, int failedAttempts) {
        log.warn("SECURITY_AUDIT: account_locked username={} ip={} failed_attempts={} timestamp={}", username, ipAddress, failedAttempts, java.time.Instant.now());
    }

    public void logAccountUnlocked(String username, String ipAddress) {
        log.info("SECURITY_AUDIT: account_unlocked username={} ip={} timestamp={}", username, ipAddress, java.time.Instant.now());
    }

    public void logLogout(String username, String ipAddress) {
        log.info("SECURITY_AUDIT: logout username={} ip={} timestamp={}", username, ipAddress, java.time.Instant.now());
    }

    public void logPasswordChanged(String username, String ipAddress) {
        log.info("SECURITY_AUDIT: password_changed username={} ip={} timestamp={}", username, ipAddress, java.time.Instant.now());
    }

    public void logPasswordResetRequested(String username, String email) {
        log.info("SECURITY_AUDIT: password_reset_requested username={} email={} timestamp={}", username, email, java.time.Instant.now());
    }

    public void logPasswordResetCompleted(String username, String ipAddress) {
        log.info("SECURITY_AUDIT: password_reset_completed username={} ip={} timestamp={}", username, ipAddress, java.time.Instant.now());
    }

    public void logTokenRefreshed(String username, String ipAddress) {
        log.info("SECURITY_AUDIT: token_refreshed username={} ip={} timestamp={}", username, ipAddress, java.time.Instant.now());
    }

    public void logTokenRevoked(String username, String ipAddress, String reason) {
        log.warn("SECURITY_AUDIT: token_revoked username={} ip={} reason={} timestamp={}", username, ipAddress, reason, java.time.Instant.now());
    }

    public void logAccessDenied(String username, String ipAddress, String requestedPath) {
        log.warn("SECURITY_AUDIT: access_denied username={} ip={} path={} timestamp={}", username, ipAddress, requestedPath, java.time.Instant.now());
    }

    public void logRegistration(String username, String email, String ipAddress) {
        log.info("SECURITY_AUDIT: registration username={} email={} ip={} timestamp={}", username, email, ipAddress, java.time.Instant.now());
    }

    public void logSuspiciousActivity(String username, String ipAddress, String activity) {
        log.error("SECURITY_AUDIT: suspicious_activity username={} ip={} activity={} timestamp={}", username, ipAddress, activity, java.time.Instant.now());
    }

    public void logMfaEnabled(String username, String method, String ipAddress) {
        log.info("SECURITY_AUDIT: mfa_enabled username={} method={} ip={} timestamp={}", username, method, ipAddress, java.time.Instant.now());
    }

    public void logMfaDisabled(String username, String ipAddress) {
        log.info("SECURITY_AUDIT: mfa_disabled username={} ip={} timestamp={}", username, ipAddress, java.time.Instant.now());
    }

    public void logMfaChallengeSent(String username, String method, String ipAddress) {
        log.info("SECURITY_AUDIT: mfa_challenge_sent username={} method={} ip={} timestamp={}", username, method, ipAddress, java.time.Instant.now());
    }

    public void logMfaSuccess(String username, String ipAddress) {
        log.info("SECURITY_AUDIT: mfa_success username={} ip={} timestamp={}", username, ipAddress, java.time.Instant.now());
    }

    public void logMfaFailure(String username, String ipAddress, String reason) {
        log.warn("SECURITY_AUDIT: mfa_failure username={} ip={} reason={} timestamp={}", username, ipAddress, reason, java.time.Instant.now());
    }
}