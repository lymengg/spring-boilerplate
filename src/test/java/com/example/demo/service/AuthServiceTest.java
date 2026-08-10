package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;
import com.example.demo.security.service.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserService userService;
    @Mock private LoginService loginService;
    @Mock private TokenService tokenService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private MfaSetupService mfaSetupService;
    @Mock private ClientIpResolver clientIpResolver;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // RequestContextHolder is not set in unit tests, so getClientIp() returns "unknown"
    }

    private Authentication auth() {
        var details = org.springframework.security.core.userdetails.User.builder()
                .username("testuser")
                .password("pass")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    @DisplayName("Should register user and return token response")
    void shouldRegisterAndReturnTokenResponse() {
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser").email("new@example.com").password("SecurePass123!").build();
        User user = User.builder().username("newuser").email("new@example.com").build();
        TokenResponse tokenResponse = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();

        when(userService.register(request, "unknown")).thenReturn(user);
        when(tokenService.generateTokenResponse(user)).thenReturn(tokenResponse);

        TokenResponse result = authService.register(request);

        assertThat(result).isEqualTo(tokenResponse);
        verify(userService).register(request, "unknown");
        verify(tokenService).generateTokenResponse(user);
    }

    @Test
    @DisplayName("Should delegate login to LoginService")
    void shouldDelegateLogin() {
        LoginRequest request = LoginRequest.builder().usernameOrEmail("testuser").password("password").build();
        TokenResponse tokenResponse = TokenResponse.builder().accessToken("access").build();

        when(loginService.login(request, "unknown")).thenReturn(tokenResponse);

        Object result = authService.login(request);

        assertThat(result).isEqualTo(tokenResponse);
        verify(loginService).login(request, "unknown");
    }

    @Test
    @DisplayName("Should delegate MFA verify to LoginService")
    void shouldDelegateMfaVerify() {
        MfaVerifyRequest request = MfaVerifyRequest.builder().mfaSessionToken("session").code("123456").build();
        TokenResponse tokenResponse = TokenResponse.builder().accessToken("access").build();

        when(loginService.verifyMfa(request, "unknown")).thenReturn(tokenResponse);

        TokenResponse result = authService.verifyMfa(request);

        assertThat(result).isEqualTo(tokenResponse);
        verify(loginService).verifyMfa(request, "unknown");
    }

    @Test
    @DisplayName("Should delegate refresh token to TokenService")
    void shouldDelegateRefreshToken() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh").build();
        TokenResponse tokenResponse = TokenResponse.builder().accessToken("new").build();

        when(tokenService.refreshToken(request, "unknown")).thenReturn(tokenResponse);

        TokenResponse result = authService.refreshToken(request);

        assertThat(result).isEqualTo(tokenResponse);
        verify(tokenService).refreshToken(request, "unknown");
    }

    @Test
    @DisplayName("Should delegate logout to TokenService")
    void shouldDelegateLogout() {
        authService.logout(auth());

        verify(tokenService).logout("testuser", "unknown");
    }

    @Test
    @DisplayName("Should delegate get current user to UserService")
    void shouldDelegateGetCurrentUser() {
        UserResponse userResponse = UserResponse.builder().username("testuser").build();

        when(userService.getCurrentUser("testuser")).thenReturn(userResponse);

        UserResponse result = authService.getCurrentUser(auth());

        assertThat(result).isEqualTo(userResponse);
    }

    @Test
    @DisplayName("Should delegate enable MFA to MfaSetupService")
    void shouldDelegateEnableMfa() {
        MfaEnableRequest request = MfaEnableRequest.builder().method(MfaMethod.TOTP).build();
        MfaSetupResponse response = MfaSetupResponse.builder().method("TOTP").build();

        when(mfaSetupService.enableMfa("testuser", request, "unknown")).thenReturn(response);

        MfaSetupResponse result = authService.enableMfa(auth(), request);

        assertThat(result).isEqualTo(response);
    }

    @Test
    @DisplayName("Should delegate change password and revoke tokens")
    void shouldDelegateChangePassword() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("old").newPassword("NewPass123!").confirmPassword("NewPass123!").build();

        authService.changePassword(auth(), request);

        verify(userService).changePassword("testuser", request, "unknown");
        verify(tokenService).revokeAllUserRefreshTokens("testuser");
    }

    @Test
    @DisplayName("Should delegate forgot password to PasswordResetService")
    void shouldDelegateForgotPassword() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email("test@example.com").build();

        authService.forgotPassword(request);

        verify(passwordResetService).forgotPassword(request, "unknown");
    }

    @Test
    @DisplayName("Should delegate reset password to PasswordResetService")
    void shouldDelegateResetPassword() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("token").newPassword("NewPass123!").confirmPassword("NewPass123!").build();

        authService.resetPassword(request);

        verify(passwordResetService).resetPassword(request, "unknown");
    }
}
