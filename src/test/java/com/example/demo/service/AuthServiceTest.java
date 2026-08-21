package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.security.service.ClientIpResolver;
import com.example.demo.service.impl.AuthServiceImpl;
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
        @Mock private ClientIpResolver clientIpResolver;

    @InjectMocks
    private AuthServiceImpl authService;

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
        TokenResponse tokenResponse = TokenResponse.builder().accessToken("new").build();

        when(tokenService.refreshToken("refresh", "unknown")).thenReturn(tokenResponse);

        TokenResponse result = authService.refreshToken("refresh");

        assertThat(result).isEqualTo(tokenResponse);
        verify(tokenService).refreshToken("refresh", "unknown");
    }

    @Test
    @DisplayName("Should delegate logout to TokenService")
    void shouldDelegateLogout() {
        authService.logout(auth(), "access-token");

        verify(tokenService).logout("testuser", "access-token", "unknown");
    }

    @Test
    @DisplayName("Should delegate get current user to UserService")
    void shouldDelegateGetCurrentUser() {
        UserProfileResponse userProfileResponse = UserProfileResponse.builder().username("testuser").build();

        when(userService.getCurrentUser("testuser")).thenReturn(userProfileResponse);

        UserProfileResponse result = authService.getCurrentUser(auth());

        assertThat(result).isEqualTo(userProfileResponse);
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
