package com.example.demo.service;

import com.example.demo.dto.*;
import org.springframework.security.core.Authentication;

public interface AuthService {

    Object login(LoginRequest request);

    TokenResponse verifyMfa(MfaVerifyRequest request);

    TokenResponse refreshToken(RefreshTokenRequest request);

    void logout(Authentication authentication);

    UserProfileResponse getCurrentUser(Authentication authentication);

    void changePassword(Authentication authentication, ChangePasswordRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void cleanupExpiredPasswordResetTokens();
}
