package com.example.demo.service.impl;

import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.service.EmailService;
import com.example.demo.service.MfaService;
import com.example.demo.service.MfaSetupService;
import com.example.demo.service.TokenService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the MFA lifecycle (enable, disable, reset). Called by UserManagementServiceImpl
 * when an admin manages MFA on behalf of a target user. The target user verifies
 * their MFA setup at next login via the existing MFA login challenge flow.
 */
@Service
@RequiredArgsConstructor
public class MfaSetupServiceImpl implements MfaSetupService {

    private final UserService userService;
    private final MfaService mfaService;
    private final EmailService emailService;
    private final SecurityAuditLogger securityAuditLogger;
    private final TokenService tokenService;

    @Override
    @Transactional
    public MfaSetupResponse enableMfa(User targetUser, MfaMethod method, String ipAddress) {
        if (targetUser.getMfaEnabled()) {
            throw new IllegalStateException("MFA is already enabled for this user. Use reset to reconfigure.");
        }

        MfaSetupResponse response = configureMfa(targetUser, method);
        targetUser.setMfaEnabled(true);
        userService.save(targetUser);
        tokenService.revokeAllUserRefreshTokens(targetUser.getUsername());
        securityAuditLogger.logMfaEnabled(targetUser.getUsername(), targetUser.getMfaMethod().name(), ipAddress);
        return response;
    }

    @Override
    @Transactional
    public void disableMfa(User targetUser, String ipAddress) {
        if (!targetUser.getMfaEnabled()) {
            throw new IllegalStateException("MFA is not enabled for this user");
        }

        targetUser.setMfaEnabled(false);
        targetUser.setMfaMethod(MfaMethod.NONE);
        targetUser.setMfaSecret(null);
        userService.save(targetUser);
        tokenService.revokeAllUserRefreshTokens(targetUser.getUsername());
        securityAuditLogger.logMfaDisabled(targetUser.getUsername(), ipAddress);
    }

    @Override
    @Transactional
    public MfaSetupResponse resetMfa(User targetUser, MfaMethod method, String ipAddress) {
        if (!targetUser.getMfaEnabled()) {
            throw new IllegalStateException("MFA is not enabled for this user. Use enable to configure.");
        }

        MfaSetupResponse response = configureMfa(targetUser, method);
        userService.save(targetUser);
        tokenService.revokeAllUserRefreshTokens(targetUser.getUsername());
        securityAuditLogger.logMfaDisabled(targetUser.getUsername(), ipAddress);
        securityAuditLogger.logMfaEnabled(targetUser.getUsername(), targetUser.getMfaMethod().name(), ipAddress);
        return response;
    }

    private MfaSetupResponse configureMfa(User targetUser, MfaMethod method) {
        if (method == MfaMethod.TOTP) {
            String secret = mfaService.generateTotpSecret();
            targetUser.setMfaSecret(secret);
            targetUser.setMfaMethod(MfaMethod.TOTP);
            String qrUri = mfaService.generateOtpAuthUri(targetUser.getUsername(), secret);
            return MfaSetupResponse.builder()
                    .qrUri(qrUri)
                    .secret(secret)
                    .method(MfaMethod.TOTP.name())
                    .build();
        } else if (method == MfaMethod.EMAIL) {
            targetUser.setMfaMethod(MfaMethod.EMAIL);
            String otp = mfaService.generateEmailOtp();
            mfaService.storeEmailOtp(targetUser.getUsername(), otp);
            emailService.sendMfaCodeEmail(targetUser.getEmail(), otp);
            return MfaSetupResponse.builder()
                    .method(MfaMethod.EMAIL.name())
                    .build();
        }

        throw new IllegalArgumentException("Invalid MFA method");
    }
}
