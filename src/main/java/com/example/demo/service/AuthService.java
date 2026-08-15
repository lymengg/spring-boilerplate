package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.security.service.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Orchestrates authentication and authorization use cases by delegating to
 * focused domain services. It contains no business logic; its only responsibility
 * is HTTP-context wiring (client IP) and dispatching to the correct domain service.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final LoginService loginService;
    private final TokenService tokenService;
    private final PasswordResetService passwordResetService;
    private final MfaSetupService mfaSetupService;
    private final ClientIpResolver clientIpResolver;

    public Object login(LoginRequest request) {
        return loginService.login(request, getClientIp());
    }

    public TokenResponse verifyMfa(MfaVerifyRequest request) {
        return loginService.verifyMfa(request, getClientIp());
    }

    public TokenResponse refreshToken(RefreshTokenRequest request) {
        return tokenService.refreshToken(request, getClientIp());
    }

    public void logout(Authentication authentication) {
        tokenService.logout(authentication.getName(), getClientIp());
    }

    public UserResponse getCurrentUser(Authentication authentication) {
        return userService.getCurrentUser(authentication.getName());
    }

    public MfaSetupResponse enableMfa(Authentication authentication, MfaEnableRequest request) {
        return mfaSetupService.enableMfa(authentication.getName(), request, getClientIp());
    }

    public void verifyMfaSetup(Authentication authentication, MfaVerifySetupRequest request) {
        mfaSetupService.verifyMfaSetup(authentication.getName(), request, getClientIp());
    }

    public void disableMfa(Authentication authentication, MfaDisableRequest request) {
        mfaSetupService.disableMfa(authentication.getName(), request, getClientIp());
    }

    public MfaStatusResponse getMfaStatus(Authentication authentication) {
        return mfaSetupService.getMfaStatus(authentication.getName());
    }

    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        String ipAddress = getClientIp();
        userService.changePassword(authentication.getName(), request, ipAddress);
        tokenService.revokeAllUserRefreshTokens(authentication.getName());
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request, getClientIp());
    }

    public void resetPassword(ResetPasswordRequest request) {
        passwordResetService.resetPassword(request, getClientIp());
    }

    public void cleanupExpiredPasswordResetTokens() {
        passwordResetService.cleanupExpiredTokens();
    }

    /**
     * Resolves the client IP from the current HTTP request so domain services can
     * audit and rate-limit without accessing the servlet layer themselves. Falls
     * back to "unknown" when called outside a request (e.g., scheduled cleanup).
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return clientIpResolver.resolveClientIp(attrs.getRequest());
        } catch (Exception e) {
            return "unknown";
        }
    }
}
