package com.example.demo.service.impl;

import com.example.demo.dto.MfaDisableRequest;
import com.example.demo.dto.MfaEnableRequest;
import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.dto.MfaStatusResponse;
import com.example.demo.dto.MfaVerifySetupRequest;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.service.EmailService;
import com.example.demo.service.MfaService;
import com.example.demo.service.MfaSetupService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the MFA lifecycle (enable, verify setup, disable, status). Isolating this
 * keeps TOTP/EMAIL specifics out of the auth orchestrator and out of LoginService.
 */
@Service
@RequiredArgsConstructor
public class MfaSetupServiceImpl implements MfaSetupService {

    private final UserService userService;
    private final MfaService mfaService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditLogger securityAuditLogger;

    @Override
    @Transactional
    public MfaSetupResponse enableMfa(String username, MfaEnableRequest request, String ipAddress) {
        User user = userService.getByUsername(username);

        if (request.getMethod() == MfaMethod.TOTP) {
            String secret = mfaService.generateTotpSecret();
            user.setMfaSecret(secret);
            user.setMfaMethod(MfaMethod.TOTP);
            userService.save(user);

            String qrUri = mfaService.generateOtpAuthUri(user.getUsername(), secret);
            return MfaSetupResponse.builder()
                    .qrUri(qrUri)
                    .secret(secret)
                    .method(MfaMethod.TOTP.name())
                    .build();
        } else if (request.getMethod() == MfaMethod.EMAIL) {
            user.setMfaMethod(MfaMethod.EMAIL);
            userService.save(user);

            String otp = mfaService.generateEmailOtp();
            mfaService.storeEmailOtp(user.getUsername(), otp);
            emailService.sendMfaCodeEmail(user.getEmail(), otp);

            return MfaSetupResponse.builder()
                    .method(MfaMethod.EMAIL.name())
                    .build();
        }

        throw new IllegalArgumentException("Invalid MFA method");
    }

    @Override
    @Transactional
    public void verifyMfaSetup(String username, MfaVerifySetupRequest request, String ipAddress) {
        User user = userService.getByUsername(username);

        if (user.getMfaMethod() == MfaMethod.NONE || (user.getMfaSecret() == null && user.getMfaMethod() == MfaMethod.TOTP)) {
            throw new IllegalStateException("MFA setup not initiated");
        }

        if (user.getMfaMethod() == MfaMethod.TOTP) {
            if (!mfaService.verifyTotpCode(user.getMfaSecret(), request.getCode())) {
                throw new BadCredentialsException("Invalid MFA code");
            }
        } else if (user.getMfaMethod() == MfaMethod.EMAIL) {
            if (!mfaService.verifyEmailOtp(user.getUsername(), request.getCode())) {
                throw new BadCredentialsException("Invalid MFA code");
            }
        }

        user.setMfaEnabled(true);
        userService.save(user);
        securityAuditLogger.logMfaEnabled(user.getUsername(), user.getMfaMethod().name(), ipAddress);
    }

    @Override
    @Transactional
    public void disableMfa(String username, MfaDisableRequest request, String ipAddress) {
        User user = userService.getByUsername(username);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setMfaEnabled(false);
        user.setMfaMethod(MfaMethod.NONE);
        user.setMfaSecret(null);
        userService.save(user);

        securityAuditLogger.logMfaDisabled(user.getUsername(), ipAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public MfaStatusResponse getMfaStatus(String username) {
        User user = userService.getByUsername(username);
        return MfaStatusResponse.builder()
                .mfaEnabled(user.getMfaEnabled())
                .method(user.getMfaMethod())
                .build();
    }
}
