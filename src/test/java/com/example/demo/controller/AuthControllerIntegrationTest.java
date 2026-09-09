package com.example.demo.controller;

import com.example.demo.config.InMemoryRedisTemplate;
import com.example.demo.dto.ChangePasswordRequest;
import com.example.demo.dto.ForgotPasswordRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.MfaVerifyRequest;
import com.example.demo.dto.ResetPasswordRequest;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.PasswordResetToken;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.cookie.AuthCookieManager;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.example.demo.security.service.TokenHashingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryRedisTemplate redisTemplate;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TokenHashingService tokenHashingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        redisTemplate.clear();
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        testUser = User.builder()
                .email("auth@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .firstName("Auth")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        testUser.getRoles().add(employeeRole);
        userRepository.save(testUser);
    }

    private LoginRequest loginRequest() {
        return LoginRequest.builder()
                .email("auth@example.com")
                .password(PASSWORD)
                .build();
    }

    private String validAccessToken() {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(testUser.getEmail());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        return jwtTokenProvider.generateAccessToken(authentication);
    }

    private Cookie accessCookie() {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, validAccessToken());
    }

    private User createMfaUser(String email, MfaMethod method) {
        User mfaUser = User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .firstName("Mfa")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .mfaEnabled(true)
                .mfaMethod(method)
                .mfaSecret("JBSWY3DPEHPK3PXP")
                .build();
        mfaUser.getRoles().add(roleRepository.findByName("EMPLOYEE").orElseThrow());
        return userRepository.save(mfaUser);
    }

    private String loginAndGetMfaSessionToken(String email, String password) throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("mfaSessionToken").asText();
    }

    private String generateTotpCode(String secret) {
        try {
            DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();
            return codeGenerator.generate(secret, Math.floorDiv(System.currentTimeMillis() / 1000, 30));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createResetToken(User user, String rawToken) {
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .tokenHash(tokenHashingService.hashToken(rawToken))
                .user(user)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        passwordResetTokenRepository.save(resetToken);
    }

    @Test
    @DisplayName("Login sets cookies and returns the profile, never tokens")
    void loginReturnsProfileAndSetsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("auth@example.com"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(result -> {
                    Cookie access = result.getResponse().getCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE);
                    Cookie refresh = result.getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
                    assertThat(access).isNotNull();
                    assertThat(access.isHttpOnly()).isTrue();
                    assertThat(access.getSecure()).isTrue();
                    assertThat(refresh).isNotNull();
                    assertThat(refresh.isHttpOnly()).isTrue();
                    assertThat(refresh.getSecure()).isTrue();
                });
    }

    @Test
    @DisplayName("Login with wrong password returns 401")
    void loginWithWrongPasswordReturns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("auth@example.com")
                .password("WrongPassword1!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getCookies()).isEmpty());
    }

    @Test
    @DisplayName("Login with an MFA-enabled user returns the challenge without cookies")
    void loginMfaReturnsChallengeWithoutCookies() throws Exception {
        User mfaUser = User.builder()
                .email("mfa@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .firstName("Mfa")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .mfaEnabled(true)
                .mfaMethod(MfaMethod.TOTP)
                .mfaSecret("JBSWY3DPEHPK3PXP")
                .build();
        mfaUser.getRoles().add(roleRepository.findByName("EMPLOYEE").orElseThrow());
        userRepository.save(mfaUser);

        LoginRequest request = LoginRequest.builder()
                .email("mfa@example.com")
                .password(PASSWORD)
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(result -> assertThat(result.getResponse().getCookies()).isEmpty());
    }

    @Test
    @DisplayName("Access token cookie authenticates a protected endpoint")
    void accessTokenCookieAuthenticates() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .cookie(accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("auth@example.com"));
    }

    @Test
    @DisplayName("Logout clears both auth cookies")
    void logoutClearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(accessCookie()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertThat(result.getResponse().getCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
                    assertThat(result.getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
                });
    }

    @Test
    @DisplayName("Refresh without a refresh token cookie returns 400")
    void refreshWithoutTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Protected endpoint without credentials returns 401")
    void protectedEndpointWithoutCredentialsReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("MFA verify with a valid TOTP code sets cookies and returns the profile")
    void mfaVerifyWithValidTotpCodeSetsCookies() throws Exception {
        createMfaUser("mfa@example.com", MfaMethod.TOTP);
        String sessionToken = loginAndGetMfaSessionToken("mfa@example.com", PASSWORD);

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MfaVerifyRequest.builder()
                                .mfaSessionToken(sessionToken)
                                .code(generateTotpCode("JBSWY3DPEHPK3PXP"))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("mfa@example.com"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(result -> {
                    Cookie access = result.getResponse().getCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE);
                    Cookie refresh = result.getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
                    assertThat(access).isNotNull();
                    assertThat(refresh).isNotNull();
                });
    }

    @Test
    @DisplayName("MFA verify with an invalid code returns 401 and no cookies")
    void mfaVerifyWithInvalidCodeReturns401() throws Exception {
        createMfaUser("mfa@example.com", MfaMethod.TOTP);
        String sessionToken = loginAndGetMfaSessionToken("mfa@example.com", PASSWORD);

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MfaVerifyRequest.builder()
                                .mfaSessionToken(sessionToken)
                                .code("000000")
                                .build())))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getCookies()).isEmpty());
    }

    @Test
    @DisplayName("MFA verify with an invalid or expired session token returns 401")
    void mfaVerifyWithInvalidSessionReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MfaVerifyRequest.builder()
                                .mfaSessionToken("bogus-session")
                                .code("123456")
                                .build())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("MFA verify with a non-numeric code returns 400")
    void mfaVerifyWithNonNumericCodeReturns400() throws Exception {
        createMfaUser("mfa@example.com", MfaMethod.TOTP);
        String sessionToken = loginAndGetMfaSessionToken("mfa@example.com", PASSWORD);

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MfaVerifyRequest.builder()
                                .mfaSessionToken(sessionToken)
                                .code("abcdef")
                                .build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MFA verify with a valid EMAIL OTP completes the login")
    void mfaVerifyWithEmailOtpSucceeds() throws Exception {
        createMfaUser("emailmfa@example.com", MfaMethod.EMAIL);
        String sessionToken = loginAndGetMfaSessionToken("emailmfa@example.com", PASSWORD);

        String storedOtp = redisTemplate.opsForValue().get("mfa_otp:emailmfa@example.com");
        assertThat(storedOtp).isNotNull();

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MfaVerifyRequest.builder()
                                .mfaSessionToken(sessionToken)
                                .code(storedOtp)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("emailmfa@example.com"));
    }

    @Test
    @DisplayName("Refresh with a valid refresh token cookie rotates the token pair")
    void refreshWithValidTokenRotatesCookies() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    Cookie newAccess = result.getResponse().getCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE);
                    Cookie newRefresh = result.getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
                    assertThat(newAccess).isNotNull();
                    assertThat(newRefresh).isNotNull();
                    assertThat(newRefresh.getValue()).isNotEqualTo(refreshCookie.getValue());
                });
    }

    @Test
    @DisplayName("Refresh with an invalid refresh token returns 401")
    void refreshWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(AuthCookieManager.REFRESH_TOKEN_COOKIE, "bogus-refresh-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Change password succeeds and clears both auth cookies")
    void changePasswordSuccessClearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ChangePasswordRequest.builder()
                                .currentPassword(PASSWORD)
                                .newPassword("NewPassword123!")
                                .confirmPassword("NewPassword123!")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertThat(result.getResponse().getCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
                    assertThat(result.getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
                });
    }

    @Test
    @DisplayName("Change password with a wrong current password returns 401")
    void changePasswordWithWrongCurrentPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ChangePasswordRequest.builder()
                                .currentPassword("WrongPassword1!")
                                .newPassword("NewPassword123!")
                                .confirmPassword("NewPassword123!")
                                .build())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Change password with mismatched confirmation returns 400")
    void changePasswordWithMismatchReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ChangePasswordRequest.builder()
                                .currentPassword(PASSWORD)
                                .newPassword("NewPassword123!")
                                .confirmPassword("Different123!")
                                .build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Change password with a weak new password returns 400")
    void changePasswordWithWeakPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .cookie(accessCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ChangePasswordRequest.builder()
                                .currentPassword(PASSWORD)
                                .newPassword("weak")
                                .confirmPassword("weak")
                                .build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Forgot password returns 200 for an existing email without leaking user existence")
    void forgotPasswordReturns200ForExistingEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ForgotPasswordRequest.builder()
                                .email("auth@example.com")
                                .build())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Reset password with a valid token succeeds and changes the password")
    void resetPasswordSuccess() throws Exception {
        createResetToken(testUser, "raw-reset-token");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ResetPasswordRequest.builder()
                                .token("raw-reset-token")
                                .newPassword("NewPassword123!")
                                .confirmPassword("NewPassword123!")
                                .build())))
                .andExpect(status().isOk());

        assertThat(passwordEncoder.matches("NewPassword123!", testUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Reset password with an invalid token returns 400")
    void resetPasswordWithInvalidTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ResetPasswordRequest.builder()
                                .token("bogus-token")
                                .newPassword("NewPassword123!")
                                .confirmPassword("NewPassword123!")
                                .build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Reset password with a weak new password returns 400")
    void resetPasswordWithWeakPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ResetPasswordRequest.builder()
                                .token("raw-reset-token")
                                .newPassword("weak")
                                .confirmPassword("weak")
                                .build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login with a locked account returns 429")
    void loginWithLockedAccountReturns429() throws Exception {
        User lockedUser = User.builder()
                .email("locked@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(false)
                .credentialsNonExpired(true)
                .failedAttempts(5)
                .accountLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();
        lockedUser.getRoles().add(roleRepository.findByName("EMPLOYEE").orElseThrow());
        userRepository.save(lockedUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("locked@example.com")
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Login with a disabled account returns 401")
    void loginWithDisabledAccountReturns401() throws Exception {
        User disabledUser = User.builder()
                .email("disabled@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .enabled(false)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        disabledUser.getRoles().add(roleRepository.findByName("EMPLOYEE").orElseThrow());
        userRepository.save(disabledUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("disabled@example.com")
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with an expired account returns 401")
    void loginWithExpiredAccountReturns401() throws Exception {
        User expiredUser = User.builder()
                .email("expired@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .accountNonExpired(false)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        expiredUser.getRoles().add(roleRepository.findByName("EMPLOYEE").orElseThrow());
        userRepository.save(expiredUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("expired@example.com")
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with expired credentials returns 401")
    void loginWithExpiredCredentialsReturns401() throws Exception {
        User expiredCredsUser = User.builder()
                .email("expiredcreds@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(false)
                .build();
        expiredCredsUser.getRoles().add(roleRepository.findByName("EMPLOYEE").orElseThrow());
        userRepository.save(expiredCredsUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("expiredcreds@example.com")
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Account locks after the max failed attempts and rejects further logins with 429")
    void accountLocksAfterMaxFailedAttempts() throws Exception {
        LoginRequest wrongPassword = LoginRequest.builder()
                .email("auth@example.com")
                .password("WrongPassword1!")
                .build();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongPassword)))
                    .andExpect(status().isUnauthorized());
        }

        // Even the correct password is rejected while the lockout is active.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Logout blacklists the access token so it can no longer authenticate")
    void logoutBlacklistsAccessToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andReturn();
        Cookie accessCookie = loginResult.getResponse().getCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE);
        assertThat(accessCookie).isNotNull();

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(accessCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .cookie(accessCookie))
                .andExpect(status().isUnauthorized());
    }
}
