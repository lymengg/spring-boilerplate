package com.example.demo.service;

import com.example.demo.dto.*;
import org.springframework.security.core.Authentication;

public interface AuthService {

    Object login(LoginRequest request);

    TokenResponse verifyMfa(MfaVerifyRequest request);

    TokenResponse refreshToken(RefreshTokenRequest request);

    void logout(Authentication authentication);

    UserProfileResponse getCurrentUser(Authentication authentication);

    MfaSetupResponse enableMfa(Authentication authentication, MfaEnableRequest request);

    MfaSetupResponse enableMfaForUser(Authentication authentication, AdminMfaEnableRequest request);

    MfaSetupResponse resetMfa(Authentication authentication, AdminMfaResetRequest request);

    void disableMfaForUser(Authentication authentication, AdminMfaDisableRequest request);

    void changePassword(Authentication authentication, ChangePasswordRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void cleanupExpiredPasswordResetTokens();
}
