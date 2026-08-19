package com.example.demo.service;

import com.example.demo.dto.AdminMfaDisableRequest;
import com.example.demo.dto.AdminMfaEnableRequest;
import com.example.demo.dto.AdminMfaResetRequest;
import com.example.demo.dto.MfaEnableRequest;
import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.impl.MfaSetupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaSetupServiceTest {

    @Mock private UserService userService;
    @Mock private MfaService mfaService;
    @Mock private EmailService emailService;
    @Mock private TokenService tokenService;
    @Mock private SecurityAuditLogger securityAuditLogger;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private MfaSetupServiceImpl mfaSetupService;

    private User adminUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .mfaEnabled(false)
                .mfaMethod(MfaMethod.NONE)
                .build();

        targetUser = User.builder()
                .id(2L)
                .username("targetuser")
                .email("target@example.com")
                .mfaEnabled(false)
                .mfaMethod(MfaMethod.NONE)
                .build();
    }

    @Test
    @DisplayName("Should enable TOTP MFA for user without existing MFA")
    void shouldEnableTotpMfa() {
        MfaEnableRequest request = MfaEnableRequest.builder().method(MfaMethod.TOTP).build();
        when(userService.getByUsername("targetuser")).thenReturn(targetUser);
        when(mfaService.generateTotpSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(mfaService.generateOtpAuthUri(eq("targetuser"), eq("JBSWY3DPEHPK3PXP"))).thenReturn("otpauth://totp/test");

        MfaSetupResponse response = mfaSetupService.enableMfa("targetuser", request, "127.0.0.1");

        assertThat(response.getMethod()).isEqualTo("TOTP");
        assertThat(response.getQrUri()).isEqualTo("otpauth://totp/test");
        assertThat(response.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        verify(userService).save(argThat(user ->
                user.getMfaEnabled() && user.getMfaMethod() == MfaMethod.TOTP && "JBSWY3DPEHPK3PXP".equals(user.getMfaSecret())
        ));
        verify(tokenService).revokeAllUserRefreshTokens("targetuser");
    }

    @Test
    @DisplayName("Should enable EMAIL MFA for user without existing MFA")
    void shouldEnableEmailMfa() {
        MfaEnableRequest request = MfaEnableRequest.builder().method(MfaMethod.EMAIL).build();
        when(userService.getByUsername("targetuser")).thenReturn(targetUser);
        when(mfaService.generateEmailOtp()).thenReturn("123456");

        MfaSetupResponse response = mfaSetupService.enableMfa("targetuser", request, "127.0.0.1");

        assertThat(response.getMethod()).isEqualTo("EMAIL");
        assertThat(response.getQrUri()).isNull();
        verify(userService).save(argThat(user ->
                user.getMfaEnabled() && user.getMfaMethod() == MfaMethod.EMAIL && user.getMfaSecret() == null
        ));
        verify(tokenService).revokeAllUserRefreshTokens("targetuser");
        verify(mfaService).storeEmailOtp("targetuser", "123456");
        verify(emailService).sendMfaCodeEmail("target@example.com", "123456");
    }

    @Test
    @DisplayName("Should reject enable MFA when MFA is already enabled")
    void shouldRejectEnableWhenMfaAlreadyEnabled() {
        targetUser.setMfaEnabled(true);
        targetUser.setMfaMethod(MfaMethod.TOTP);
        MfaEnableRequest request = MfaEnableRequest.builder().method(MfaMethod.TOTP).build();
        when(userService.getByUsername("targetuser")).thenReturn(targetUser);

        assertThatThrownBy(() -> mfaSetupService.enableMfa("targetuser", request, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA is already enabled");
    }

    @Test
    @DisplayName("Should reject enable with NONE method")
    void shouldRejectEnableWithNoneMethod() {
        MfaEnableRequest request = MfaEnableRequest.builder().method(MfaMethod.NONE).build();
        when(userService.getByUsername("targetuser")).thenReturn(targetUser);

        assertThatThrownBy(() -> mfaSetupService.enableMfa("targetuser", request, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MFA method");
    }

    @Test
    @DisplayName("Admin should enable MFA for another user")
    void shouldAdminEnableMfaForUser() {
        AdminMfaEnableRequest request = AdminMfaEnableRequest.builder()
                .targetUserId(2L)
                .method(MfaMethod.TOTP)
                .build();
        when(userService.getByUsername("admin")).thenReturn(adminUser);
        when(authorizationService.isSuperAdmin(adminUser)).thenReturn(true);
        when(userService.getById(2L)).thenReturn(targetUser);
        when(mfaService.generateTotpSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(mfaService.generateOtpAuthUri(eq("targetuser"), eq("JBSWY3DPEHPK3PXP"))).thenReturn("otpauth://totp/test");

        MfaSetupResponse response = mfaSetupService.enableMfaForUser("admin", request, "127.0.0.1");

        assertThat(response.getMethod()).isEqualTo("TOTP");
        verify(securityAuditLogger).logMfaEnabled("targetuser", "TOTP", "127.0.0.1");
        verify(tokenService).revokeAllUserRefreshTokens("targetuser");
    }

    @Test
    @DisplayName("Admin should not enable MFA for user in different tenant")
    void shouldRejectAdminEnableForDifferentTenant() {
        User tenantAdmin = User.builder()
                .id(3L)
                .username("tenantadmin")
                .mfaEnabled(false)
                .mfaMethod(MfaMethod.NONE)
                .build();
        User otherTenantUser = User.builder()
                .id(4L)
                .username("otheruser")
                .mfaEnabled(false)
                .mfaMethod(MfaMethod.NONE)
                .build();

        AdminMfaEnableRequest request = AdminMfaEnableRequest.builder()
                .targetUserId(4L)
                .method(MfaMethod.TOTP)
                .build();
        when(userService.getByUsername("tenantadmin")).thenReturn(tenantAdmin);
        when(authorizationService.isSuperAdmin(tenantAdmin)).thenReturn(false);

        assertThatThrownBy(() -> mfaSetupService.enableMfaForUser("tenantadmin", request, "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cannot manage MFA");
    }

    @Test
    @DisplayName("Admin should reset MFA for user with existing MFA")
    void shouldAdminResetMfa() {
        targetUser.setMfaEnabled(true);
        targetUser.setMfaMethod(MfaMethod.TOTP);
        targetUser.setMfaSecret("OLD_SECRET");

        AdminMfaResetRequest request = AdminMfaResetRequest.builder()
                .targetUserId(2L)
                .method(MfaMethod.TOTP)
                .build();
        when(userService.getByUsername("admin")).thenReturn(adminUser);
        when(authorizationService.isSuperAdmin(adminUser)).thenReturn(true);
        when(userService.getById(2L)).thenReturn(targetUser);
        when(mfaService.generateTotpSecret()).thenReturn("NEW_SECRET");
        when(mfaService.generateOtpAuthUri(eq("targetuser"), eq("NEW_SECRET"))).thenReturn("otpauth://totp/test");

        MfaSetupResponse response = mfaSetupService.resetMfa("admin", request, "127.0.0.1");

        assertThat(response.getMethod()).isEqualTo("TOTP");
        assertThat(response.getSecret()).isEqualTo("NEW_SECRET");
        verify(userService).save(argThat(user ->
                "NEW_SECRET".equals(user.getMfaSecret()) && user.getMfaEnabled()
        ));
        verify(tokenService).revokeAllUserRefreshTokens("targetuser");
        verify(securityAuditLogger).logMfaReset("targetuser", "TOTP", "127.0.0.1");
    }

    @Test
    @DisplayName("Should reject reset MFA when MFA is not configured")
    void shouldRejectResetWhenMfaNotConfigured() {
        AdminMfaResetRequest request = AdminMfaResetRequest.builder()
                .targetUserId(2L)
                .method(MfaMethod.TOTP)
                .build();
        when(userService.getByUsername("admin")).thenReturn(adminUser);
        when(authorizationService.isSuperAdmin(adminUser)).thenReturn(true);
        when(userService.getById(2L)).thenReturn(targetUser);

        assertThatThrownBy(() -> mfaSetupService.resetMfa("admin", request, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA is not configured");
    }

    @Test
    @DisplayName("Admin should disable MFA for user")
    void shouldAdminDisableMfa() {
        targetUser.setMfaEnabled(true);
        targetUser.setMfaMethod(MfaMethod.TOTP);
        targetUser.setMfaSecret("SECRET");

        AdminMfaDisableRequest request = AdminMfaDisableRequest.builder()
                .targetUserId(2L)
                .build();
        when(userService.getByUsername("admin")).thenReturn(adminUser);
        when(authorizationService.isSuperAdmin(adminUser)).thenReturn(true);
        when(userService.getById(2L)).thenReturn(targetUser);

        mfaSetupService.disableMfaForUser("admin", request, "127.0.0.1");

        verify(userService).save(argThat(user ->
                !user.getMfaEnabled() && user.getMfaMethod() == MfaMethod.NONE && user.getMfaSecret() == null
        ));
        verify(tokenService).revokeAllUserRefreshTokens("targetuser");
        verify(securityAuditLogger).logMfaDisabledByAdmin("targetuser", "admin", "127.0.0.1");
    }

    @Test
    @DisplayName("Should reject disable MFA when MFA is not configured")
    void shouldRejectDisableWhenMfaNotConfigured() {
        AdminMfaDisableRequest request = AdminMfaDisableRequest.builder()
                .targetUserId(2L)
                .build();
        when(userService.getByUsername("admin")).thenReturn(adminUser);
        when(authorizationService.isSuperAdmin(adminUser)).thenReturn(true);
        when(userService.getById(2L)).thenReturn(targetUser);

        assertThatThrownBy(() -> mfaSetupService.disableMfaForUser("admin", request, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA is not configured");
    }

    @Test
    @DisplayName("Should revoke sessions on MFA enable")
    void shouldRevokeSessionsOnEnable() {
        MfaEnableRequest request = MfaEnableRequest.builder().method(MfaMethod.TOTP).build();
        when(userService.getByUsername("targetuser")).thenReturn(targetUser);
        when(mfaService.generateTotpSecret()).thenReturn("SECRET");
        when(mfaService.generateOtpAuthUri(anyString(), anyString())).thenReturn("otpauth://totp/test");

        mfaSetupService.enableMfa("targetuser", request, "127.0.0.1");

        verify(tokenService).revokeAllUserRefreshTokens("targetuser");
    }

    @Test
    @DisplayName("Should revoke sessions on MFA reset")
    void shouldRevokeSessionsOnReset() {
        targetUser.setMfaEnabled(true);
        targetUser.setMfaMethod(MfaMethod.TOTP);

        AdminMfaResetRequest request = AdminMfaResetRequest.builder()
                .targetUserId(2L)
                .method(MfaMethod.TOTP)
                .build();
        when(userService.getByUsername("admin")).thenReturn(adminUser);
        when(authorizationService.isSuperAdmin(adminUser)).thenReturn(true);
        when(userService.getById(2L)).thenReturn(targetUser);
        when(mfaService.generateTotpSecret()).thenReturn("NEW_SECRET");
        when(mfaService.generateOtpAuthUri(anyString(), anyString())).thenReturn("otpauth://totp/test");

        mfaSetupService.resetMfa("admin", request, "127.0.0.1");

        verify(tokenService).revokeAllUserRefreshTokens("targetuser");
    }

    @Test
    @DisplayName("Should revoke sessions on MFA disable")
    void shouldRevokeSessionsOnDisable() {
        targetUser.setMfaEnabled(true);
        targetUser.setMfaMethod(MfaMethod.TOTP);

        AdminMfaDisableRequest request = AdminMfaDisableRequest.builder()
                .targetUserId(2L)
                .build();
        when(userService.getByUsername("admin")).thenReturn(adminUser);
        when(authorizationService.isSuperAdmin(adminUser)).thenReturn(true);
        when(userService.getById(2L)).thenReturn(targetUser);

        mfaSetupService.disableMfaForUser("admin", request, "127.0.0.1");

        verify(tokenService).revokeAllUserRefreshTokens("targetuser");
    }
}
