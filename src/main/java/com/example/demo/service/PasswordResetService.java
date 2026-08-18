package com.example.demo.service;

import com.example.demo.dto.ForgotPasswordRequest;
import com.example.demo.dto.ResetPasswordRequest;

public interface PasswordResetService {

    void forgotPassword(ForgotPasswordRequest request, String ipAddress);

    void resetPassword(ResetPasswordRequest request, String ipAddress);

    void cleanupExpiredTokens();
}
