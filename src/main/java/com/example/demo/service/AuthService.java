package com.example.demo.service;

import com.example.demo.dto.*;
import org.springframework.security.core.Authentication;

public interface AuthService {

    LoginResult login(LoginRequest request);

    TokenResponse verifyMfa(MfaVerifyRequest request);

    TokenResponse refreshToken(String refreshToken);

    void logout(Authentication authentication, String accessToken);

    UserProfileResponse getCurrentUser(Authentication authentication);

    UserProfileResponse getUserProfile(String username);

    void changePassword(Authentication authentication, ChangePasswordRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void cleanupExpiredPasswordResetTokens();
}
