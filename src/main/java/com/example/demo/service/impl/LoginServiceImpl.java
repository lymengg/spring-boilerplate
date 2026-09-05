package com.example.demo.service.impl;

import com.example.demo.config.MfaProperties;
import com.example.demo.config.SecurityProperties;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResult;
import com.example.demo.dto.MfaLoginResponse;
import com.example.demo.dto.MfaVerifyRequest;
import com.example.demo.dto.TokenResponse;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.service.RateLimitingService;
import com.example.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encapsulates the complete login flow: credential verification, lockout
 * handling, MFA challenge, and MFA verification. Separated from the auth
 * orchestrator so cross-cutting security rules are cohesive and testable.
 */
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final AccountLockoutService accountLockoutService;
    private final TokenService tokenService;
    private final MfaService mfaService;
    private final EmailService emailService;
    private final RateLimitingService rateLimitingService;
    private final MfaProperties mfaProperties;
    private final SecurityProperties securityProperties;
    private final SecurityAuditLogger securityAuditLogger;

    @Override
    @Transactional
    public LoginResult login(LoginRequest request, String ipAddress) {
        User user = userService.getByUsernameOrEmail(request.getUsernameOrEmail());

        accountLockoutService.prepareForLogin(user, ipAddress);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsernameOrEmail(),
                            request.getPassword()
                    )
            );

            accountLockoutService.recordSuccessfulLogin(user, ipAddress);

            if (user.getMfaEnabled() && user.getMfaMethod() != MfaMethod.NONE) {
                String mfaSessionToken = mfaService.storeMfaPendingSession(user.getUsername());

                if (user.getMfaMethod() == MfaMethod.EMAIL) {
                    String otp = mfaService.generateEmailOtp();
                    mfaService.storeEmailOtp(user.getUsername(), otp);
                    emailService.sendMfaCodeEmail(user.getEmail(), otp);
                }

                securityAuditLogger.logMfaChallengeSent(user.getUsername(), user.getMfaMethod().name(), ipAddress);

                return new LoginResult.MfaChallenge(MfaLoginResponse.builder()
                        .mfaRequired(true)
                        .mfaSessionToken(mfaSessionToken)
                        .method(user.getMfaMethod().name())
                        .expiresIn(mfaProperties.getPendingTokenExpiration() / 1000)
                        .build());
            }

            TokenResponse tokenResponse = tokenService.generateTokenResponse(user);
            securityAuditLogger.logLoginSuccess(user.getUsername(), ipAddress);
            return new LoginResult.TokenSuccess(tokenResponse);

        } catch (BadCredentialsException e) {
            accountLockoutService.recordFailedLogin(user, ipAddress);
            throw e;
        }
    }

    @Override
    @Transactional
    public TokenResponse verifyMfa(MfaVerifyRequest request, String ipAddress) {
        String sessionToken = request.getMfaSessionToken();

        String username = mfaService.validateMfaPendingSession(sessionToken);
        if (username == null) {
            securityAuditLogger.logMfaFailure("unknown", ipAddress, "Invalid or expired MFA session token");
            throw new BadCredentialsException("Invalid or expired MFA session token");
        }

        int maxRequests = securityProperties.getRateLimiting().getPerUser().getMfaVerify();
        long windowMillis = securityProperties.getRateLimiting().getWindowMillis();
        if (!rateLimitingService.isAllowed("mfa-verify", username, maxRequests, windowMillis)) {
            throw new LockedException("Too many requests. Please try again later.");
        }

        User user = userService.getByUsername(username);

        boolean verified = false;
        if (user.getMfaMethod() == MfaMethod.TOTP) {
            verified = mfaService.verifyTotpCode(user.getMfaSecret(), request.getCode());
        } else if (user.getMfaMethod() == MfaMethod.EMAIL) {
            verified = mfaService.verifyEmailOtp(user.getUsername(), request.getCode());
        }

        if (!verified) {
            securityAuditLogger.logMfaFailure(user.getUsername(), ipAddress, "Invalid MFA code");
            throw new BadCredentialsException("Invalid MFA code");
        }

        mfaService.revokeMfaPendingSession(sessionToken);
        TokenResponse tokenResponse = tokenService.generateTokenResponse(user);

        securityAuditLogger.logMfaSuccess(user.getUsername(), ipAddress);
        securityAuditLogger.logLoginSuccess(user.getUsername(), ipAddress);

        return tokenResponse;
    }
}
