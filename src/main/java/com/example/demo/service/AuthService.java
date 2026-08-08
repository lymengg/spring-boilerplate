package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.config.AppProperties;
import com.example.demo.config.JwtConfig;
import com.example.demo.config.MfaProperties;
import com.example.demo.config.SecurityProperties;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.PasswordResetToken;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.example.demo.security.service.RefreshTokenService;
import com.example.demo.security.service.TokenHashingService;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final RefreshTokenService refreshTokenService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenHashingService tokenHashingService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final ModelMapper modelMapper;
    private final SecurityProperties securityProperties;
    private final AppProperties appProperties;
    private final SecurityAuditLogger securityAuditLogger;
    private final MfaService mfaService;
    private final MfaProperties mfaProperties;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Default role USER not found"));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        
        user.getRoles().add(userRole);
        user = userRepository.save(user);

        securityAuditLogger.logRegistration(user.getUsername(), user.getEmail(), getClientIp());

        return generateTokenResponse(user);
    }

    @Transactional
    public Object login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.unlockIfExpired()) {
            userRepository.save(user);
            securityAuditLogger.logAccountUnlocked(user.getUsername(), getClientIp());
        }

        if (!user.isAccountNonLocked()) {
            securityAuditLogger.logAccountLocked(user.getUsername(), getClientIp(), user.getFailedAttempts());
            throw new LockedException("Account is locked due to too many failed attempts. Try again later.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsernameOrEmail(),
                            request.getPassword()
                    )
            );

            user.unlockAccount();
            userRepository.save(user);

            if (user.getMfaEnabled() && user.getMfaMethod() != MfaMethod.NONE) {
                String mfaSessionToken = mfaService.storeMfaPendingSession(user.getUsername());

                if (user.getMfaMethod() == MfaMethod.EMAIL) {
                    String otp = mfaService.generateEmailOtp();
                    mfaService.storeEmailOtp(user.getUsername(), otp);
                    emailService.sendMfaCodeEmail(user.getEmail(), otp);
                }

                securityAuditLogger.logMfaChallengeSent(user.getUsername(), user.getMfaMethod().name(), getClientIp());

                return MfaLoginResponse.builder()
                        .mfaRequired(true)
                        .mfaSessionToken(mfaSessionToken)
                        .method(user.getMfaMethod().name())
                        .expiresIn(mfaProperties.getPendingTokenExpiration() / 1000)
                        .build();
            }

            securityAuditLogger.logLoginSuccess(user.getUsername(), getClientIp());
            refreshTokenService.revokeAllUserRefreshTokens(user.getUsername());

            return generateTokenResponse(user);

        } catch (BadCredentialsException e) {
            user.incrementFailedAttempts();
            if (user.getFailedAttempts() >= securityProperties.getAccountLockout().getMaxAttempts()) {
                user.lockAccount(securityProperties.getAccountLockout().getLockoutDurationMinutes());
                securityAuditLogger.logAccountLocked(user.getUsername(), getClientIp(), user.getFailedAttempts());
            }
            userRepository.save(user);
            securityAuditLogger.logLoginFailure(user.getUsername(), getClientIp(), "Bad credentials");
            throw e;
        }
    }

    @Transactional
    public TokenResponse verifyMfa(MfaVerifyRequest request) {
        String sessionToken = request.getMfaSessionToken();

        String username = mfaService.validateMfaPendingSession(sessionToken);
        if (username == null) {
            securityAuditLogger.logMfaFailure("unknown", getClientIp(), "Invalid or expired MFA session token");
            throw new BadCredentialsException("Invalid or expired MFA session token");
        }

        User user = userDetailsService.loadUserEntityByUsername(username);

        boolean verified = false;
        if (user.getMfaMethod() == MfaMethod.TOTP) {
            verified = mfaService.verifyTotpCode(user.getMfaSecret(), request.getCode());
        } else if (user.getMfaMethod() == MfaMethod.EMAIL) {
            verified = mfaService.verifyEmailOtp(user.getUsername(), request.getCode());
        }

        if (!verified) {
            securityAuditLogger.logMfaFailure(user.getUsername(), getClientIp(), "Invalid MFA code");
            throw new BadCredentialsException("Invalid MFA code");
        }

        mfaService.revokeMfaPendingSession(sessionToken);
        securityAuditLogger.logMfaSuccess(user.getUsername(), getClientIp());
        securityAuditLogger.logLoginSuccess(user.getUsername(), getClientIp());
        refreshTokenService.revokeAllUserRefreshTokens(user.getUsername());

        return generateTokenResponse(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        
        if (!refreshTokenService.validateRefreshToken(username, refreshToken)) {
            throw new BadCredentialsException("Refresh token not found or revoked");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication authentication = jwtTokenProvider.getAuthentication(refreshToken);
        
        String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        refreshTokenService.revokeRefreshToken(refreshToken);
        refreshTokenService.storeRefreshToken(username, newRefreshToken, jwtConfig.getRefreshTokenExpiration());

        User user = userDetailsService.loadUserEntityByUsername(username);
        securityAuditLogger.logTokenRefreshed(user.getUsername(), getClientIp());
        
        return buildTokenResponse(newAccessToken, newRefreshToken, user);
    }

    public void logout(Authentication authentication) {
        String username = authentication.getName();
        refreshTokenService.revokeAllUserRefreshTokens(username);
        securityAuditLogger.logLogout(username, getClientIp());
        log.info("User {} logged out successfully", username);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        User user = userDetailsService.loadUserEntityByUsername(authentication.getName());
        UserResponse response = modelMapper.map(user, UserResponse.class);
        response.setRoles(user.getRoles().stream().map(Role::getName).toArray(String[]::new));
        return response;
    }

    @Transactional
    public MfaSetupResponse enableMfa(Authentication authentication, MfaEnableRequest request) {
        User user = userDetailsService.loadUserEntityByUsername(authentication.getName());

        if (request.getMethod() == MfaMethod.TOTP) {
            String secret = mfaService.generateTotpSecret();
            user.setMfaSecret(secret);
            user.setMfaMethod(MfaMethod.TOTP);
            userRepository.save(user);

            String qrUri = mfaService.generateOtpAuthUri(user.getUsername(), secret);
            return MfaSetupResponse.builder()
                    .qrUri(qrUri)
                    .secret(secret)
                    .method(MfaMethod.TOTP.name())
                    .build();
        } else if (request.getMethod() == MfaMethod.EMAIL) {
            user.setMfaMethod(MfaMethod.EMAIL);
            userRepository.save(user);

            String otp = mfaService.generateEmailOtp();
            mfaService.storeEmailOtp(user.getUsername(), otp);
            emailService.sendMfaCodeEmail(user.getEmail(), otp);

            return MfaSetupResponse.builder()
                    .method(MfaMethod.EMAIL.name())
                    .build();
        }

        throw new IllegalArgumentException("Invalid MFA method");
    }

    @Transactional
    public void verifyMfaSetup(Authentication authentication, MfaVerifySetupRequest request) {
        User user = userDetailsService.loadUserEntityByUsername(authentication.getName());

        if (user.getMfaMethod() == MfaMethod.NONE || user.getMfaSecret() == null && user.getMfaMethod() == MfaMethod.TOTP) {
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
        userRepository.save(user);
        securityAuditLogger.logMfaEnabled(user.getUsername(), user.getMfaMethod().name(), getClientIp());
    }

    @Transactional
    public void disableMfa(Authentication authentication, MfaDisableRequest request) {
        User user = userDetailsService.loadUserEntityByUsername(authentication.getName());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setMfaEnabled(false);
        user.setMfaMethod(MfaMethod.NONE);
        user.setMfaSecret(null);
        userRepository.save(user);

        securityAuditLogger.logMfaDisabled(user.getUsername(), getClientIp());
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse getMfaStatus(Authentication authentication) {
        User user = userDetailsService.loadUserEntityByUsername(authentication.getName());
        return MfaStatusResponse.builder()
                .mfaEnabled(user.getMfaEnabled())
                .method(user.getMfaMethod())
                .build();
    }

    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        User user = userDetailsService.loadUserEntityByUsername(authentication.getName());
        
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        refreshTokenService.revokeAllUserRefreshTokens(user.getUsername());
        
        securityAuditLogger.logPasswordChanged(user.getUsername(), getClientIp());
        log.info("Password changed for user: {}", user.getUsername());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String rawToken = tokenHashingService.generateSecureToken();
            String tokenHash = tokenHashingService.hashToken(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .tokenHash(tokenHash)
                    .user(user)
                    .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String resetLink = appProperties.getBaseUrl() + "/api/auth/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

            securityAuditLogger.logPasswordResetRequested(user.getUsername(), user.getEmail());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String tokenHash = tokenHashingService.hashToken(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("Reset token has expired or already been used");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        refreshTokenService.revokeAllUserRefreshTokens(user.getUsername());
        securityAuditLogger.logPasswordResetCompleted(user.getUsername(), getClientIp());
        log.info("Password reset completed for user: {}", user.getUsername());
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredPasswordResetTokens() {
        int deleted = passwordResetTokenRepository.deleteExpiredAndUsed(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired/used password reset tokens", deleted);
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            var request = attrs.getRequest();
            String xfHeader = request.getHeader("X-Forwarded-For");
            if (xfHeader != null && !xfHeader.isEmpty()) {
                return xfHeader.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private TokenResponse generateTokenResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        
        refreshTokenService.storeRefreshToken(user.getUsername(), refreshToken, jwtConfig.getRefreshTokenExpiration());
        
        return buildTokenResponse(accessToken, refreshToken, user);
    }

    private TokenResponse buildTokenResponse(String accessToken, String refreshToken, User user) {
        String[] roles = user.getRoles().stream()
                .map(Role::getName)
                .toArray(String[]::new);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtConfig.getAccessTokenExpiration() / 1000)
                .username(user.getUsername())
                .roles(roles)
                .build();
    }
}