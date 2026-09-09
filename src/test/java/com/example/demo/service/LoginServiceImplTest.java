package com.example.demo.service;

import com.example.demo.config.MfaProperties;
import com.example.demo.config.SecurityProperties;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResult;
import com.example.demo.dto.MfaVerifyRequest;
import com.example.demo.dto.TokenResponse;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.service.RateLimitingService;
import com.example.demo.service.impl.LoginServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginServiceImplTest {

    @Mock private UserService userService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private AccountLockoutService accountLockoutService;
    @Mock private TokenService tokenService;
    @Mock private MfaService mfaService;
    @Mock private EmailService emailService;
    @Mock private RateLimitingService rateLimitingService;
    @Mock private MfaProperties mfaProperties;
    @Mock private SecurityProperties securityProperties;
    @Mock private SecurityAuditLogger securityAuditLogger;

    @InjectMocks
    private LoginServiceImpl loginService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@example.com")
                .password("encoded-password")
                .mfaEnabled(false)
                .mfaMethod(MfaMethod.NONE)
                .build();

        when(mfaProperties.getPendingTokenExpiration()).thenReturn(300_000L);

        SecurityProperties.RateLimiting rateLimiting = new SecurityProperties.RateLimiting();
        rateLimiting.getPerUser().setMfaVerify(10);
        when(securityProperties.getRateLimiting()).thenReturn(rateLimiting);
    }

    private LoginRequest loginRequest() {
        return LoginRequest.builder()
                .email("test@example.com")
                .password("Password123!")
                .build();
    }

    @Test
    @DisplayName("Login without MFA returns tokens and records the successful login")
    void loginWithoutMfaReturnsTokens() {
        TokenResponse tokenResponse = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();
        when(userService.getByEmail("test@example.com")).thenReturn(user);
        when(tokenService.generateTokenResponse(user)).thenReturn(tokenResponse);

        LoginResult result = loginService.login(loginRequest(), "1.2.3.4");

        assertThat(result).isInstanceOf(LoginResult.TokenSuccess.class);
        assertThat(((LoginResult.TokenSuccess) result).tokenResponse()).isEqualTo(tokenResponse);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(accountLockoutService).recordSuccessfulLogin(user, "1.2.3.4");
        verify(securityAuditLogger).logLoginSuccess("test@example.com", "1.2.3.4");
        verify(mfaService, never()).storeMfaPendingSession(any());
    }

    @Test
    @DisplayName("Login with TOTP MFA returns a challenge without issuing tokens")
    void loginWithTotpMfaReturnsChallenge() {
        user.setMfaEnabled(true);
        user.setMfaMethod(MfaMethod.TOTP);
        when(userService.getByEmail("test@example.com")).thenReturn(user);
        when(mfaService.storeMfaPendingSession("test@example.com")).thenReturn("session-token");

        LoginResult result = loginService.login(loginRequest(), "1.2.3.4");

        assertThat(result).isInstanceOf(LoginResult.MfaChallenge.class);
        LoginResult.MfaChallenge challenge = (LoginResult.MfaChallenge) result;
        assertThat(challenge.mfaResponse().isMfaRequired()).isTrue();
        assertThat(challenge.mfaResponse().getMfaSessionToken()).isEqualTo("session-token");
        assertThat(challenge.mfaResponse().getMethod()).isEqualTo("TOTP");
        assertThat(challenge.mfaResponse().getExpiresIn()).isEqualTo(300);
        verify(tokenService, never()).generateTokenResponse(any());
        verify(emailService, never()).sendMfaCodeEmail(any(), any());
        verify(securityAuditLogger).logMfaChallengeSent("test@example.com", "TOTP", "1.2.3.4");
    }

    @Test
    @DisplayName("Login with EMAIL MFA generates, stores and sends an OTP")
    void loginWithEmailMfaSendsOtp() {
        user.setMfaEnabled(true);
        user.setMfaMethod(MfaMethod.EMAIL);
        when(userService.getByEmail("test@example.com")).thenReturn(user);
        when(mfaService.storeMfaPendingSession("test@example.com")).thenReturn("session-token");
        when(mfaService.generateEmailOtp()).thenReturn("123456");

        LoginResult result = loginService.login(loginRequest(), "1.2.3.4");

        assertThat(result).isInstanceOf(LoginResult.MfaChallenge.class);
        assertThat(((LoginResult.MfaChallenge) result).mfaResponse().getMethod()).isEqualTo("EMAIL");
        verify(mfaService).storeEmailOtp("test@example.com", "123456");
        verify(emailService).sendMfaCodeEmail("test@example.com", "123456");
    }

    @Test
    @DisplayName("Login with bad credentials records the failed attempt and rethrows")
    void loginWithBadCredentialsRecordsFailure() {
        when(userService.getByEmail("test@example.com")).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> loginService.login(loginRequest(), "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);

        verify(accountLockoutService).recordFailedLogin(user, "1.2.3.4");
        verify(accountLockoutService, never()).recordSuccessfulLogin(any(), any());
    }

    @Test
    @DisplayName("Login with a locked account propagates LockedException")
    void loginWithLockedAccountPropagatesLocked() {
        when(userService.getByEmail("test@example.com")).thenReturn(user);
        when(accountLockoutService.prepareForLogin(user, "1.2.3.4"))
                .thenThrow(new LockedException("Account is locked"));

        assertThatThrownBy(() -> loginService.login(loginRequest(), "1.2.3.4"))
                .isInstanceOf(LockedException.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("MFA verify with a valid TOTP code issues tokens and revokes the session")
    void verifyMfaWithValidTotpCodeIssuesTokens() {
        when(mfaService.validateMfaPendingSession("session-token")).thenReturn("test@example.com");
        when(rateLimitingService.isAllowed("mfa-verify", "test@example.com", 10, 60_000L)).thenReturn(true);
        when(userService.getByEmail("test@example.com")).thenReturn(user);
        user.setMfaEnabled(true);
        user.setMfaMethod(MfaMethod.TOTP);
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        when(mfaService.verifyTotpCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);
        TokenResponse tokenResponse = TokenResponse.builder().accessToken("access").build();
        when(tokenService.generateTokenResponse(user)).thenReturn(tokenResponse);

        TokenResponse result = loginService.verifyMfa(
                MfaVerifyRequest.builder().mfaSessionToken("session-token").code("123456").build(),
                "1.2.3.4");

        assertThat(result).isEqualTo(tokenResponse);
        verify(mfaService).revokeMfaPendingSession("session-token");
        verify(securityAuditLogger).logMfaSuccess("test@example.com", "1.2.3.4");
        verify(securityAuditLogger).logLoginSuccess("test@example.com", "1.2.3.4");
    }

    @Test
    @DisplayName("MFA verify with a valid EMAIL OTP issues tokens")
    void verifyMfaWithValidEmailOtpIssuesTokens() {
        when(mfaService.validateMfaPendingSession("session-token")).thenReturn("test@example.com");
        when(rateLimitingService.isAllowed("mfa-verify", "test@example.com", 10, 60_000L)).thenReturn(true);
        when(userService.getByEmail("test@example.com")).thenReturn(user);
        user.setMfaEnabled(true);
        user.setMfaMethod(MfaMethod.EMAIL);
        when(mfaService.verifyEmailOtp("test@example.com", "123456")).thenReturn(true);
        TokenResponse tokenResponse = TokenResponse.builder().accessToken("access").build();
        when(tokenService.generateTokenResponse(user)).thenReturn(tokenResponse);

        TokenResponse result = loginService.verifyMfa(
                MfaVerifyRequest.builder().mfaSessionToken("session-token").code("123456").build(),
                "1.2.3.4");

        assertThat(result).isEqualTo(tokenResponse);
        verify(mfaService).revokeMfaPendingSession("session-token");
    }

    @Test
    @DisplayName("MFA verify with an invalid or expired session token returns 401")
    void verifyMfaRejectsInvalidSession() {
        when(mfaService.validateMfaPendingSession("bad-session")).thenReturn(null);

        assertThatThrownBy(() -> loginService.verifyMfa(
                MfaVerifyRequest.builder().mfaSessionToken("bad-session").code("123456").build(),
                "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);

        verify(securityAuditLogger).logMfaFailure("unknown", "1.2.3.4", "Invalid or expired MFA session token");
        verify(tokenService, never()).generateTokenResponse(any());
    }

    @Test
    @DisplayName("MFA verify with an invalid code returns 401 and does not revoke the session")
    void verifyMfaRejectsInvalidCode() {
        when(mfaService.validateMfaPendingSession("session-token")).thenReturn("test@example.com");
        when(rateLimitingService.isAllowed("mfa-verify", "test@example.com", 10, 60_000L)).thenReturn(true);
        when(userService.getByEmail("test@example.com")).thenReturn(user);
        user.setMfaEnabled(true);
        user.setMfaMethod(MfaMethod.TOTP);
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        when(mfaService.verifyTotpCode("JBSWY3DPEHPK3PXP", "000000")).thenReturn(false);

        assertThatThrownBy(() -> loginService.verifyMfa(
                MfaVerifyRequest.builder().mfaSessionToken("session-token").code("000000").build(),
                "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);

        verify(securityAuditLogger).logMfaFailure("test@example.com", "1.2.3.4", "Invalid MFA code");
        verify(mfaService, never()).revokeMfaPendingSession(any());
        verify(tokenService, never()).generateTokenResponse(any());
    }

    @Test
    @DisplayName("MFA verify is rate limited per user")
    void verifyMfaIsRateLimited() {
        when(mfaService.validateMfaPendingSession("session-token")).thenReturn("test@example.com");
        when(rateLimitingService.isAllowed("mfa-verify", "test@example.com", 10, 60_000L)).thenReturn(false);

        assertThatThrownBy(() -> loginService.verifyMfa(
                MfaVerifyRequest.builder().mfaSessionToken("session-token").code("123456").build(),
                "1.2.3.4"))
                .isInstanceOf(LockedException.class);

        verify(userService, never()).getByEmail(any());
    }
}
