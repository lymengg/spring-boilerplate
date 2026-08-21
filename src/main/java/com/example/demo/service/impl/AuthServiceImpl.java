package com.example.demo.service.impl;

import com.example.demo.dto.*;
import com.example.demo.security.service.ClientIpResolver;
import com.example.demo.service.*;
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
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final LoginService loginService;
    private final TokenService tokenService;
    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    @Override
    public Object login(LoginRequest request) {
        return loginService.login(request, getClientIp());
    }

    @Override
    public TokenResponse verifyMfa(MfaVerifyRequest request) {
        return loginService.verifyMfa(request, getClientIp());
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        return tokenService.refreshToken(refreshToken, getClientIp());
    }

    @Override
    public void logout(Authentication authentication, String accessToken) {
        tokenService.logout(authentication.getName(), accessToken, getClientIp());
    }

    @Override
    public UserProfileResponse getCurrentUser(Authentication authentication) {
        return userService.getCurrentUser(authentication.getName());
    }

    @Override
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        String ipAddress = getClientIp();
        userService.changePassword(authentication.getName(), request, ipAddress);
        tokenService.revokeAllUserRefreshTokens(authentication.getName());
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request, getClientIp());
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        passwordResetService.resetPassword(request, getClientIp());
    }

    @Override
    public void cleanupExpiredPasswordResetTokens() {
        passwordResetService.cleanupExpiredTokens();
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return clientIpResolver.resolveClientIp(attrs.getRequest());
        } catch (Exception e) {
            return "unknown";
        }
    }
}
