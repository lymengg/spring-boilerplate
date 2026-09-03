package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.cookie.AuthCookieService;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Transactional;

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
    private static final String FRONTEND_ORIGIN = "http://localhost:3000";

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

    private User testUser;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        testUser = User.builder()
                .username("authuser")
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
                .usernameOrEmail("authuser")
                .password(PASSWORD)
                .build();
    }

    private String validAccessToken() {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(testUser.getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        return jwtTokenProvider.generateAccessToken(authentication);
    }

    @Test
    @DisplayName("Login (API client, no Origin) returns tokens in the body and sets httpOnly cookies")
    void loginApiClientReturnsTokensAndCookies() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(result -> {
                    Cookie access = result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
                    Cookie refresh = result.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
                    assertThat(access).isNotNull();
                    assertThat(access.isHttpOnly()).isTrue();
                    assertThat(access.getSecure()).isTrue();
                    assertThat(refresh).isNotNull();
                    assertThat(refresh.isHttpOnly()).isTrue();
                    assertThat(refresh.getSecure()).isTrue();
                });
    }

    @Test
    @DisplayName("Login (browser flow, Origin present) sets cookies and returns the profile, never tokens")
    void loginBrowserFlowReturnsProfileWithoutTokens() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("authuser"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(result -> {
                    assertThat(result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE)).isNotNull();
                    assertThat(result.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE)).isNotNull();
                });
    }

    @Test
    @DisplayName("Login with wrong password returns 401")
    void loginWithWrongPasswordReturns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .usernameOrEmail("authuser")
                .password("WrongPassword1!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getCookies()).isEmpty());
    }

    @Test
    @DisplayName("Login with an MFA-enabled user returns the challenge without cookies")
    void loginMfaReturnsChallengeWithoutCookies() throws Exception {
        User mfaUser = User.builder()
                .username("mfauser")
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
                .usernameOrEmail("mfauser")
                .password(PASSWORD)
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", FRONTEND_ORIGIN)
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
                        .cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, validAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("authuser"));
    }

    @Test
    @DisplayName("Logout clears both auth cookies")
    void logoutClearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Origin", FRONTEND_ORIGIN)
                        .cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, validAccessToken())))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertThat(result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
                    assertThat(result.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
                });
    }

    @Test
    @DisplayName("Refresh without a refresh token returns 400")
    void refreshWithoutTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Refresh with an invalid refresh token returns 401")
    void refreshWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint without credentials returns 401")
    void protectedEndpointWithoutCredentialsReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
