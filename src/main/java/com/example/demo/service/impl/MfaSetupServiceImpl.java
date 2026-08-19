package com.example.demo.service.impl;

import com.example.demo.dto.AdminMfaDisableRequest;
import com.example.demo.dto.AdminMfaEnableRequest;
import com.example.demo.dto.AdminMfaResetRequest;
import com.example.demo.dto.MfaEnableRequest;
import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.EmailService;
import com.example.demo.service.MfaService;
import com.example.demo.service.MfaSetupService;
import com.example.demo.service.TokenService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the MFA lifecycle (enable, reset, disable). Isolating this keeps
 * TOTP/EMAIL specifics out of the auth orchestrator and out of LoginService.
 */
@Service
@RequiredArgsConstructor
public class MfaSetupServiceImpl implements MfaSetupService {

    private final UserService userService;
    private final MfaService mfaService;
    private final EmailService emailService;
    private final TokenService tokenService;
    private final SecurityAuditLogger securityAuditLogger;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public MfaSetupResponse enableMfa(String username, MfaEnableRequest request, String ipAddress) {
        User user = userService.getByUsername(username);

        if (user.getMfaEnabled() && user.getMfaMethod() != MfaMethod.NONE) {
            throw new IllegalStateException("MFA is already enabled. Use reset to reconfigure MFA.");
        }

        return establishMfaConfiguration(user, request.getMethod(), ipAddress);
    }

    @Override
    @Transactional
    public MfaSetupResponse enableMfaForUser(String adminUsername, AdminMfaEnableRequest request, String ipAddress) {
        User admin = userService.getByUsername(adminUsername);
        User user = findAccessibleUser(request.getTargetUserId(), admin);

        if (user.getMfaEnabled() && user.getMfaMethod() != MfaMethod.NONE) {
            throw new IllegalStateException("MFA is already enabled for user " + user.getUsername() + ". Use reset to reconfigure MFA.");
        }

        MfaSetupResponse response = establishMfaConfiguration(user, request.getMethod(), ipAddress);

        securityAuditLogger.logMfaEnabled(user.getUsername(), request.getMethod().name(), ipAddress);

        return response;
    }

    @Override
    @Transactional
    public MfaSetupResponse resetMfa(String adminUsername, AdminMfaResetRequest request, String ipAddress) {
        User admin = userService.getByUsername(adminUsername);
        User user = findAccessibleUser(request.getTargetUserId(), admin);

        if (user.getMfaMethod() == MfaMethod.NONE) {
            throw new IllegalStateException("MFA is not configured for user " + user.getUsername() + ". Use enable to set up MFA.");
        }

        MfaSetupResponse response = establishMfaConfiguration(user, request.getMethod(), ipAddress);

        securityAuditLogger.logMfaReset(user.getUsername(), request.getMethod().name(), ipAddress);

        return response;
    }

    @Override
    @Transactional
    public void disableMfaForUser(String adminUsername, AdminMfaDisableRequest request, String ipAddress) {
        User admin = userService.getByUsername(adminUsername);
        User user = findAccessibleUser(request.getTargetUserId(), admin);

        if (user.getMfaMethod() == MfaMethod.NONE) {
            throw new IllegalStateException("MFA is not configured for user " + user.getUsername());
        }

        user.setMfaEnabled(false);
        user.setMfaMethod(MfaMethod.NONE);
        user.setMfaSecret(null);
        userService.save(user);

        tokenService.revokeAllUserRefreshTokens(user.getUsername());

        securityAuditLogger.logMfaDisabledByAdmin(user.getUsername(), adminUsername, ipAddress);
    }

    private MfaSetupResponse establishMfaConfiguration(User user, MfaMethod method, String ipAddress) {
        if (method == MfaMethod.TOTP) {
            String secret = mfaService.generateTotpSecret();
            user.setMfaSecret(secret);
            user.setMfaMethod(MfaMethod.TOTP);
            user.setMfaEnabled(true);
            userService.save(user);

            tokenService.revokeAllUserRefreshTokens(user.getUsername());

            String qrUri = mfaService.generateOtpAuthUri(user.getUsername(), secret);
            return MfaSetupResponse.builder()
                    .qrUri(qrUri)
                    .secret(secret)
                    .method(MfaMethod.TOTP.name())
                    .build();
        } else if (method == MfaMethod.EMAIL) {
            user.setMfaMethod(MfaMethod.EMAIL);
            user.setMfaSecret(null);
            user.setMfaEnabled(true);
            userService.save(user);

            tokenService.revokeAllUserRefreshTokens(user.getUsername());

            String otp = mfaService.generateEmailOtp();
            mfaService.storeEmailOtp(user.getUsername(), otp);
            emailService.sendMfaCodeEmail(user.getEmail(), otp);

            return MfaSetupResponse.builder()
                    .method(MfaMethod.EMAIL.name())
                    .build();
        }

        throw new IllegalArgumentException("Invalid MFA method");
    }

    private User findAccessibleUser(Long id, User currentUser) {
        if (authorizationService.isSuperAdmin(currentUser)) {
            return userService.getById(id);
        }
        if (currentUser.getTenant() == null) {
            throw new AccessDeniedException("Cannot manage MFA for other users");
        }
        return userService.getByIdAndTenantId(id, currentUser.getTenant().getId());
    }
}
