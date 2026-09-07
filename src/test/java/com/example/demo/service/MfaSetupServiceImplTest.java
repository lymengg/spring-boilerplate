package com.example.demo.service;

import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.service.impl.MfaSetupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaSetupServiceImplTest {

    private static final String IP = "192.168.1.10";

    @Mock
    private UserService userService;

    @Mock
    private MfaService mfaService;

    @Mock
    private EmailService emailService;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private MfaSetupServiceImpl mfaSetupService;

    private User targetUser;

    @BeforeEach
    void setUp() {
        targetUser = User.builder()
                .id(1L)
                .username("target")
                .email("target@example.com")
                .password("secret")
                .mfaEnabled(false)
                .mfaMethod(MfaMethod.NONE)
                .mfaSecret(null)
                .build();

        when(mfaService.generateTotpSecret()).thenReturn("SECRET123");
        when(mfaService.generateOtpAuthUri("target", "SECRET123")).thenReturn("otpauth://totp/target?secret=SECRET123");
        when(mfaService.generateEmailOtp()).thenReturn("123456");
        when(userService.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Enabling TOTP generates a secret, persists it, revokes tokens, and audits")
    void enableTotp() {
        MfaSetupResponse response = mfaSetupService.enableMfa(targetUser, MfaMethod.TOTP, IP);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getMfaSecret()).isEqualTo("SECRET123");
        assertThat(saved.getMfaMethod()).isEqualTo(MfaMethod.TOTP);
        assertThat(saved.getMfaEnabled()).isTrue();
        verify(tokenService).revokeAllUserRefreshTokens("target");
        verify(securityAuditLogger).logMfaEnabled("target", "TOTP", IP);
        assertThat(response.getQrUri()).isEqualTo("otpauth://totp/target?secret=SECRET123");
        assertThat(response.getSecret()).isEqualTo("SECRET123");
        assertThat(response.getMethod()).isEqualTo("TOTP");
    }

    @Test
    @DisplayName("Enabling EMAIL stores the OTP, sends the email, and returns no secret")
    void enableEmail() {
        MfaSetupResponse response = mfaSetupService.enableMfa(targetUser, MfaMethod.EMAIL, IP);

        verify(mfaService).generateEmailOtp();
        verify(mfaService).storeEmailOtp("target", "123456");
        verify(emailService).sendMfaCodeEmail("target@example.com", "123456");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        assertThat(captor.getValue().getMfaMethod()).isEqualTo(MfaMethod.EMAIL);
        assertThat(captor.getValue().getMfaEnabled()).isTrue();
        verify(tokenService).revokeAllUserRefreshTokens("target");
        verify(securityAuditLogger).logMfaEnabled("target", "EMAIL", IP);
        assertThat(response.getMethod()).isEqualTo("EMAIL");
        assertThat(response.getSecret()).isNull();
        assertThat(response.getQrUri()).isNull();
    }

    @Test
    @DisplayName("Enabling MFA when already enabled throws IllegalStateException and mutates nothing")
    void enableWhenAlreadyEnabled() {
        targetUser.setMfaEnabled(true);

        assertThatThrownBy(() -> mfaSetupService.enableMfa(targetUser, MfaMethod.TOTP, IP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MFA is already enabled for this user. Use reset to reconfigure.");
        verify(userService, never()).save(any());
        verify(tokenService, never()).revokeAllUserRefreshTokens(anyString());
        verify(securityAuditLogger, never()).logMfaEnabled(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Enabling MFA with MfaMethod.NONE throws IllegalArgumentException and mutates nothing")
    void enableWithNoneMethodFails() {
        assertThatThrownBy(() -> mfaSetupService.enableMfa(targetUser, MfaMethod.NONE, IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid MFA method");
        verify(userService, never()).save(any());
        verify(tokenService, never()).revokeAllUserRefreshTokens(anyString());
        verify(securityAuditLogger, never()).logMfaEnabled(anyString(), anyString(), anyString());
        assertThat(targetUser.getMfaEnabled()).isFalse();
        assertThat(targetUser.getMfaMethod()).isEqualTo(MfaMethod.NONE);
        assertThat(targetUser.getMfaSecret()).isNull();
    }

    @Test
    @DisplayName("Enabling MFA with a null method throws IllegalArgumentException and mutates nothing")
    void enableWithNullMethodFails() {
        assertThatThrownBy(() -> mfaSetupService.enableMfa(targetUser, null, IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid MFA method");
        verify(userService, never()).save(any());
        verify(tokenService, never()).revokeAllUserRefreshTokens(anyString());
        verify(securityAuditLogger, never()).logMfaEnabled(anyString(), anyString(), anyString());
        assertThat(targetUser.getMfaEnabled()).isFalse();
        assertThat(targetUser.getMfaMethod()).isEqualTo(MfaMethod.NONE);
        assertThat(targetUser.getMfaSecret()).isNull();
    }

    @Test
    @DisplayName("Disabling MFA clears the flags, saves, revokes tokens, and audits")
    void disable() {
        targetUser.setMfaEnabled(true);
        targetUser.setMfaMethod(MfaMethod.TOTP);
        targetUser.setMfaSecret("SECRET123");

        mfaSetupService.disableMfa(targetUser, IP);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getMfaEnabled()).isFalse();
        assertThat(saved.getMfaMethod()).isEqualTo(MfaMethod.NONE);
        assertThat(saved.getMfaSecret()).isNull();
        verify(tokenService).revokeAllUserRefreshTokens("target");
        verify(securityAuditLogger).logMfaDisabled("target", IP);
    }

    @Test
    @DisplayName("Disabling MFA when not enabled throws IllegalStateException and mutates nothing")
    void disableWhenNotEnabled() {
        assertThatThrownBy(() -> mfaSetupService.disableMfa(targetUser, IP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MFA is not enabled for this user");
        verify(userService, never()).save(any());
        verify(tokenService, never()).revokeAllUserRefreshTokens(anyString());
        verify(securityAuditLogger, never()).logMfaDisabled(anyString(), anyString());
    }

    @Test
    @DisplayName("Resetting TOTP applies a new secret and records both disable and enable audits")
    void resetTotp() {
        targetUser.setMfaEnabled(true);
        targetUser.setMfaMethod(MfaMethod.TOTP);
        targetUser.setMfaSecret("OLDSECRET");

        MfaSetupResponse response = mfaSetupService.resetMfa(targetUser, MfaMethod.TOTP, IP);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getMfaSecret()).isEqualTo("SECRET123");
        assertThat(saved.getMfaMethod()).isEqualTo(MfaMethod.TOTP);
        assertThat(saved.getMfaEnabled()).isTrue();
        verify(tokenService).revokeAllUserRefreshTokens("target");
        verify(securityAuditLogger).logMfaDisabled("target", IP);
        verify(securityAuditLogger).logMfaEnabled("target", "TOTP", IP);
        assertThat(response.getQrUri()).isEqualTo("otpauth://totp/target?secret=SECRET123");
        assertThat(response.getSecret()).isEqualTo("SECRET123");
        assertThat(response.getMethod()).isEqualTo("TOTP");
    }

    @Test
    @DisplayName("Resetting EMAIL stores a new OTP, sends the email, and records both audits")
    void resetEmail() {
        targetUser.setMfaEnabled(true);
        targetUser.setMfaMethod(MfaMethod.EMAIL);

        MfaSetupResponse response = mfaSetupService.resetMfa(targetUser, MfaMethod.EMAIL, IP);

        verify(mfaService).generateEmailOtp();
        verify(mfaService).storeEmailOtp("target", "123456");
        verify(emailService).sendMfaCodeEmail("target@example.com", "123456");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        assertThat(captor.getValue().getMfaMethod()).isEqualTo(MfaMethod.EMAIL);
        assertThat(captor.getValue().getMfaEnabled()).isTrue();
        verify(tokenService).revokeAllUserRefreshTokens("target");
        verify(securityAuditLogger).logMfaDisabled("target", IP);
        verify(securityAuditLogger).logMfaEnabled("target", "EMAIL", IP);
        assertThat(response.getMethod()).isEqualTo("EMAIL");
        assertThat(response.getSecret()).isNull();
    }

    @Test
    @DisplayName("Resetting MFA when not enabled throws IllegalStateException and mutates nothing")
    void resetWhenNotEnabled() {
        assertThatThrownBy(() -> mfaSetupService.resetMfa(targetUser, MfaMethod.TOTP, IP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MFA is not enabled for this user. Use enable to configure.");
        verify(userService, never()).save(any());
        verify(tokenService, never()).revokeAllUserRefreshTokens(anyString());
        verify(securityAuditLogger, never()).logMfaDisabled(anyString(), anyString());
        verify(securityAuditLogger, never()).logMfaEnabled(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Resetting MFA with an invalid method throws IllegalArgumentException")
    void resetWithInvalidMethod() {
        targetUser.setMfaEnabled(true);

        assertThatThrownBy(() -> mfaSetupService.resetMfa(targetUser, MfaMethod.NONE, IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid MFA method");
        verify(userService, never()).save(any());
        verify(tokenService, never()).revokeAllUserRefreshTokens(anyString());
    }
}
