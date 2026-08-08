package com.example.demo.service;

import com.example.demo.config.AppProperties;
import com.example.demo.config.JwtConfig;
import com.example.demo.config.MfaProperties;
import com.example.demo.config.SecurityProperties;
import com.example.demo.dto.*;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.PasswordResetToken;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.example.demo.security.service.RefreshTokenService;
import com.example.demo.security.service.TokenHashingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Slf4j
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtConfig jwtConfig;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private TokenHashingService tokenHashingService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private ModelMapper modelMapper;
    @Mock private SecurityProperties securityProperties;
    @Mock private AppProperties appProperties;
    @Mock private SecurityAuditLogger securityAuditLogger;
    @Mock private MfaService mfaService;
    @Mock private MfaProperties mfaProperties;

    @InjectMocks
    private AuthService authService;

    private final String VALID_TOKEN = "valid.token.here";
    private final String ENCODED_PASSWORD = "$2a$12$encodedpassword";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, authenticationManager,
                jwtTokenProvider, jwtConfig, refreshTokenService, userDetailsService, tokenHashingService,
                passwordResetTokenRepository, emailService, modelMapper, securityProperties, appProperties,
                securityAuditLogger, mfaService, mfaProperties);

        SecurityProperties.AccountLockout lockout = new SecurityProperties.AccountLockout();
        lockout.setMaxAttempts(5);
        lockout.setLockoutDurationMinutes(15);
        when(securityProperties.getAccountLockout()).thenReturn(lockout);

        when(jwtConfig.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(jwtConfig.getAccessTokenExpiration()).thenReturn(900000L);

        when(appProperties.getBaseUrl()).thenReturn("http://localhost:8080");
        when(mfaProperties.getPendingTokenExpiration()).thenReturn(300000L);
    }

    private User createTestUser() {
        Role role = Role.builder().name("USER").build();
        return User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password(ENCODED_PASSWORD)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .failedAttempts(0)
                .roles(new HashSet<>(Set.of(role)))
                .build();
    }

    private Authentication createAuthentication(User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should register user successfully when username and email are unique")
        void shouldRegisterSuccessfully() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .password("SecurePass123!")
                    .firstName("John")
                    .lastName("Doe")
                    .build();

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("SecurePass123!")).thenReturn(ENCODED_PASSWORD);
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));

            User savedUser = createTestUser();
            savedUser.setUsername("newuser");
            savedUser.setEmail("new@example.com");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("newuser")
                    .password(ENCODED_PASSWORD)
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();
            when(userDetailsService.loadUserByUsername("newuser")).thenReturn(userDetails);
            Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            when(jwtTokenProvider.generateAccessToken(auth)).thenReturn("access.token");
            when(jwtTokenProvider.generateRefreshToken(auth)).thenReturn(VALID_TOKEN);

            TokenResponse response = authService.register(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access.token");
            assertThat(response.getRefreshToken()).isEqualTo(VALID_TOKEN);
            verify(userRepository).save(any(User.class));
            verify(securityAuditLogger).logRegistration(eq("newuser"), eq("new@example.com"), anyString());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when username already exists")
        void shouldThrowWhenUsernameExists() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("existing")
                    .email("new@example.com")
                    .password("SecurePass123!")
                    .build();

            when(userRepository.existsByUsername("existing")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Username already exists");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when email already exists")
        void shouldThrowWhenEmailExists() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("newuser")
                    .email("existing@example.com")
                    .password("SecurePass123!")
                    .build();

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Email already exists");
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void shouldLoginSuccessfully() {
            User user = createTestUser();
            LoginRequest request = new LoginRequest();
            request.setUsernameOrEmail("testuser");
            request.setPassword("password");

            when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));

            Authentication auth = createAuthentication(user);
            when(authenticationManager.authenticate(any())).thenReturn(auth);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("testuser")
                    .password(ENCODED_PASSWORD)
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
            when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access.token");
            when(jwtTokenProvider.generateRefreshToken(any())).thenReturn(VALID_TOKEN);

            TokenResponse response = (TokenResponse) authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("access.token");
            assertThat(response.getRefreshToken()).isEqualTo(VALID_TOKEN);
            assertThat(user.getFailedAttempts()).isEqualTo(0);
            verify(refreshTokenService).revokeAllUserRefreshTokens("testuser");
            verify(securityAuditLogger).logLoginSuccess("testuser", "unknown");
        }

        @Test
        @DisplayName("Should throw BadCredentialsException for invalid password")
        void shouldThrowForInvalidPassword() {
            User user = createTestUser();
            LoginRequest request = new LoginRequest();
            request.setUsernameOrEmail("testuser");
            request.setPassword("wrongpassword");

            when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Bad credentials");

            assertThat(user.getFailedAttempts()).isEqualTo(1);
            verify(securityAuditLogger).logLoginFailure("testuser", "unknown", "Bad credentials");
        }

        @Test
        @DisplayName("Should return MFA challenge when TOTP is enabled")
        void shouldReturnMfaChallengeWhenTotpEnabled() {
            User user = createTestUser();
            user.setMfaEnabled(true);
            user.setMfaMethod(MfaMethod.TOTP);
            user.setMfaSecret("JBSWY3DPEHPK3PXP");
            LoginRequest request = new LoginRequest();
            request.setUsernameOrEmail("testuser");
            request.setPassword("password");

            when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));
            Authentication auth = createAuthentication(user);
            when(authenticationManager.authenticate(any())).thenReturn(auth);
            when(mfaService.storeMfaPendingSession("testuser")).thenReturn("mfa-session-token");

            Object result = authService.login(request);

            assertThat(result).isInstanceOf(MfaLoginResponse.class);
            MfaLoginResponse mfaResponse = (MfaLoginResponse) result;
            assertThat(mfaResponse.isMfaRequired()).isTrue();
            assertThat(mfaResponse.getMfaSessionToken()).isEqualTo("mfa-session-token");
            assertThat(mfaResponse.getMethod()).isEqualTo("TOTP");
            verify(securityAuditLogger).logMfaChallengeSent("testuser", "TOTP", "unknown");
            verify(mfaService, never()).generateEmailOtp();
        }

        @Test
        @DisplayName("Should return MFA challenge and send email OTP when EMAIL is enabled")
        void shouldReturnMfaChallengeAndSendEmailWhenEmailEnabled() {
            User user = createTestUser();
            user.setMfaEnabled(true);
            user.setMfaMethod(MfaMethod.EMAIL);
            LoginRequest request = new LoginRequest();
            request.setUsernameOrEmail("testuser");
            request.setPassword("password");

            when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));
            Authentication auth = createAuthentication(user);
            when(authenticationManager.authenticate(any())).thenReturn(auth);
            when(mfaService.storeMfaPendingSession("testuser")).thenReturn("mfa-session-token");
            when(mfaService.generateEmailOtp()).thenReturn("123456");

            Object result = authService.login(request);

            assertThat(result).isInstanceOf(MfaLoginResponse.class);
            MfaLoginResponse mfaResponse = (MfaLoginResponse) result;
            assertThat(mfaResponse.isMfaRequired()).isTrue();
            assertThat(mfaResponse.getMethod()).isEqualTo("EMAIL");
            verify(mfaService).storeEmailOtp("testuser", "123456");
            verify(emailService).sendMfaCodeEmail("test@example.com", "123456");
        }

        @Test
        @DisplayName("Should lock account after max failed attempts")
        void shouldLockAccountAfterMaxFailures() {
            User user = createTestUser();
            user.setFailedAttempts(4);
            LoginRequest request = new LoginRequest();
            request.setUsernameOrEmail("testuser");
            request.setPassword("wrongpassword");

            when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);

            assertThat(user.getFailedAttempts()).isEqualTo(5);
            assertThat(user.isAccountNonLocked()).isFalse();
            verify(securityAuditLogger).logAccountLocked("testuser", "unknown", 5);
        }

        @Test
        @DisplayName("Should throw LockedException when account is already locked")
        void shouldThrowLockedWhenAlreadyLocked() {
            User user = createTestUser();
            user.setAccountNonLocked(false);
            user.setAccountLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));

            LoginRequest request = new LoginRequest();
            request.setUsernameOrEmail("testuser");
            request.setPassword("password");

            when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(LockedException.class)
                    .hasMessage("Account is locked due to too many failed attempts. Try again later.");

            verify(securityAuditLogger).logAccountLocked("testuser", "unknown", 0);
        }

        @Test
        @DisplayName("Should unlock account automatically after lockout duration expires")
        void shouldUnlockAccountAfterLockoutExpiry() {
            User user = createTestUser();
            user.setAccountNonLocked(false);
            user.setAccountLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));

            LoginRequest request = new LoginRequest();
            request.setUsernameOrEmail("testuser");
            request.setPassword("password");

            when(userRepository.findByUsernameOrEmail("testuser", "testuser")).thenReturn(Optional.of(user));

            Authentication auth = createAuthentication(user);
            when(authenticationManager.authenticate(any())).thenReturn(auth);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("testuser")
                    .password(ENCODED_PASSWORD)
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
            when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access.token");
            when(jwtTokenProvider.generateRefreshToken(any())).thenReturn(VALID_TOKEN);

            TokenResponse response = (TokenResponse) authService.login(request);

            assertThat(response).isNotNull();
            assertThat(user.isAccountNonLocked()).isTrue();
            assertThat(user.getFailedAttempts()).isEqualTo(0);
            verify(securityAuditLogger).logAccountUnlocked("testuser", "unknown");
            verify(userRepository, atLeastOnce()).save(user);
        }

        @Test
        @DisplayName("Should throw BadCredentialsException when user not found")
        void shouldThrowWhenUserNotFound() {
            LoginRequest request = new LoginRequest();
            request.setUsernameOrEmail("nonexistent");
            request.setPassword("password");

            when(userRepository.findByUsernameOrEmail("nonexistent", "nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should refresh access token successfully")
        void shouldRefreshAccessTokenSuccessfully() {
            User user = createTestUser();
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken(VALID_TOKEN);

            when(jwtTokenProvider.validateRefreshToken(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(VALID_TOKEN)).thenReturn("testuser");
            when(refreshTokenService.validateRefreshToken("testuser", VALID_TOKEN)).thenReturn(true);

            Authentication auth = createAuthentication(user);
            when(jwtTokenProvider.getAuthentication(VALID_TOKEN)).thenReturn(auth);
            when(jwtTokenProvider.generateAccessToken(auth)).thenReturn("new.access.token");
            when(jwtTokenProvider.generateRefreshToken(auth)).thenReturn("new.refresh.token");
            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);

            TokenResponse response = authService.refreshToken(request);

            assertThat(response.getAccessToken()).isEqualTo("new.access.token");
            assertThat(response.getRefreshToken()).isEqualTo("new.refresh.token");
            verify(refreshTokenService).revokeRefreshToken(VALID_TOKEN);
            verify(refreshTokenService).storeRefreshToken(eq("testuser"), eq("new.refresh.token"), anyLong());
            verify(securityAuditLogger).logTokenRefreshed("testuser", "unknown");
        }

        @Test
        @DisplayName("Should throw BadCredentialsException for invalid refresh token")
        void shouldThrowForInvalidRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("invalid.token");

            when(jwtTokenProvider.validateRefreshToken("invalid.token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        @DisplayName("Should throw BadCredentialsException for revoked refresh token")
        void shouldThrowForRevokedRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken(VALID_TOKEN);

            when(jwtTokenProvider.validateRefreshToken(VALID_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(VALID_TOKEN)).thenReturn("testuser");
            when(refreshTokenService.validateRefreshToken("testuser", VALID_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Refresh token not found or revoked");
        }
    }

    @Nested
    @DisplayName("Logout Tests")
    class LogoutTests {

        @Test
        @DisplayName("Should logout successfully and revoke all refresh tokens")
        void shouldLogoutSuccessfully() {
            User user = createTestUser();
            Authentication auth = createAuthentication(user);

            authService.logout(auth);

            verify(refreshTokenService).revokeAllUserRefreshTokens("testuser");
            verify(securityAuditLogger).logLogout("testuser", "unknown");
        }
    }

    @Nested
    @DisplayName("Change Password Tests")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password successfully")
        void shouldChangePasswordSuccessfully() {
            User user = createTestUser();
            Authentication auth = createAuthentication(user);
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("oldpassword");
            request.setNewPassword("NewSecurePass123!");
            request.setConfirmPassword("NewSecurePass123!");

            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(passwordEncoder.matches("oldpassword", ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncoder.encode("NewSecurePass123!")).thenReturn("$2a$12$newencoded");

            authService.changePassword(auth, request);

            verify(passwordEncoder).encode("NewSecurePass123!");
            verify(userRepository).save(user);
            verify(refreshTokenService).revokeAllUserRefreshTokens("testuser");
            verify(securityAuditLogger).logPasswordChanged("testuser", "unknown");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when passwords do not match")
        void shouldThrowWhenPasswordsDoNotMatch() {
            User user = createTestUser();
            Authentication auth = createAuthentication(user);
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("oldpassword");
            request.setNewPassword("NewSecurePass123!");
            request.setConfirmPassword("DifferentPass123!");

            assertThatThrownBy(() -> authService.changePassword(auth, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Passwords do not match");
            verifyNoInteractions(passwordEncoder, userRepository);
        }

        @Test
        @DisplayName("Should throw BadCredentialsException when current password is incorrect")
        void shouldThrowWhenCurrentPasswordIncorrect() {
            User user = createTestUser();
            Authentication auth = createAuthentication(user);
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("wrongpassword");
            request.setNewPassword("NewSecurePass123!");
            request.setConfirmPassword("NewSecurePass123!");

            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(passwordEncoder.matches("wrongpassword", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(auth, request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Current password is incorrect");
        }
    }

    @Nested
    @DisplayName("Forgot Password Tests")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Should send reset email when user exists")
        void shouldSendResetEmailWhenUserExists() {
            User user = createTestUser();
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("test@example.com");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(tokenHashingService.generateSecureToken()).thenReturn("securetoken123");
            when(tokenHashingService.hashToken("securetoken123")).thenReturn("hashedtoken");

            authService.forgotPassword(request);

            ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordResetTokenRepository).save(tokenCaptor.capture());
            PasswordResetToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getTokenHash()).isEqualTo("hashedtoken");
            assertThat(savedToken.getUser()).isEqualTo(user);
            assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());

            verify(emailService).sendPasswordResetEmail(
                    eq("test@example.com"),
                    eq("http://localhost:8080/api/auth/reset-password?token=securetoken123")
            );
            verify(securityAuditLogger).logPasswordResetRequested("testuser", "test@example.com");
        }

        @Test
        @DisplayName("Should not throw when email does not exist (prevent enumeration)")
        void shouldNotThrowWhenEmailDoesNotExist() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("nonexistent@example.com");

            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            assertThatCode(() -> authService.forgotPassword(request)).doesNotThrowAnyException();
            verifyNoInteractions(emailService, tokenHashingService, passwordResetTokenRepository);
        }
    }

    @Nested
    @DisplayName("Reset Password Tests")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should reset password successfully with valid token")
        void shouldResetPasswordSuccessfully() {
            User user = createTestUser();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .tokenHash("hashedtoken")
                    .user(user)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .build();

            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("rawtoken");
            request.setNewPassword("NewSecurePass123!");
            request.setConfirmPassword("NewSecurePass123!");

            when(tokenHashingService.hashToken("rawtoken")).thenReturn("hashedtoken");
            when(passwordResetTokenRepository.findByTokenHash("hashedtoken")).thenReturn(Optional.of(resetToken));
            when(passwordEncoder.encode("NewSecurePass123!")).thenReturn("$2a$12$newencoded");

            authService.resetPassword(request);

            assertThat(resetToken.getUsedAt()).isNotNull();
            verify(userRepository).save(user);
            verify(passwordResetTokenRepository).save(resetToken);
            verify(refreshTokenService).revokeAllUserRefreshTokens("testuser");
            verify(securityAuditLogger).logPasswordResetCompleted("testuser", "unknown");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when token not found")
        void shouldThrowWhenTokenNotFound() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("invalidtoken");
            request.setNewPassword("NewSecurePass123!");
            request.setConfirmPassword("NewSecurePass123!");

            when(tokenHashingService.hashToken("invalidtoken")).thenReturn("nonexistenthash");
            when(passwordResetTokenRepository.findByTokenHash("nonexistenthash")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid or expired reset token");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when passwords do not match")
        void shouldThrowWhenPasswordsDoNotMatch() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("sometoken");
            request.setNewPassword("NewSecurePass123!");
            request.setConfirmPassword("DifferentPass123!");

            assertThatThrownBy(() -> authService.resetPassword(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Passwords do not match");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when token is expired")
        void shouldThrowWhenTokenExpired() {
            User user = createTestUser();
            PasswordResetToken expiredToken = PasswordResetToken.builder()
                    .tokenHash("expiredhash")
                    .user(user)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .usedAt(null)
                    .build();

            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("expiredtoken");
            request.setNewPassword("NewSecurePass123!");
            request.setConfirmPassword("NewSecurePass123!");

            when(tokenHashingService.hashToken("expiredtoken")).thenReturn("expiredhash");
            when(passwordResetTokenRepository.findByTokenHash("expiredhash")).thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> authService.resetPassword(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Reset token has expired or already been used");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when token is already used")
        void shouldThrowWhenTokenAlreadyUsed() {
            User user = createTestUser();
            PasswordResetToken usedToken = PasswordResetToken.builder()
                    .tokenHash("usedhash")
                    .user(user)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .usedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                    .build();

            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("usedtoken");
            request.setNewPassword("NewSecurePass123!");
            request.setConfirmPassword("NewSecurePass123!");

            when(tokenHashingService.hashToken("usedtoken")).thenReturn("usedhash");
            when(passwordResetTokenRepository.findByTokenHash("usedhash")).thenReturn(Optional.of(usedToken));

            assertThatThrownBy(() -> authService.resetPassword(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Reset token has expired or already been used");
        }
    }

    @Nested
    @DisplayName("Get Current User Tests")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Should return current user response")
        void shouldGetCurrentUser() {
            User user = createTestUser();
            Authentication auth = createAuthentication(user);

            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            UserResponse mockResponse = UserResponse.builder().username("testuser").build();
            when(modelMapper.map(user, UserResponse.class)).thenReturn(mockResponse);

            UserResponse response = authService.getCurrentUser(auth);

            assertThat(response.getUsername()).isEqualTo("testuser");
            verify(modelMapper).map(user, UserResponse.class);
        }
    }

    @Nested
    @DisplayName("MFA Verify Tests")
    class MfaVerifyTests {

        @Test
        @DisplayName("Should verify TOTP code successfully and return tokens")
        void shouldVerifyTotpCodeSuccessfully() {
            User user = createTestUser();
            user.setMfaEnabled(true);
            user.setMfaMethod(MfaMethod.TOTP);
            user.setMfaSecret("JBSWY3DPEHPK3PXP");

            MfaVerifyRequest request = MfaVerifyRequest.builder()
                    .mfaSessionToken("mfa-session-token")
                    .code("123456")
                    .build();

            when(mfaService.validateMfaPendingSession("mfa-session-token")).thenReturn("testuser");
            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(mfaService.verifyTotpCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("testuser")
                    .password(ENCODED_PASSWORD)
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
            when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access.token");
            when(jwtTokenProvider.generateRefreshToken(any())).thenReturn(VALID_TOKEN);

            TokenResponse response = authService.verifyMfa(request);

            assertThat(response.getAccessToken()).isEqualTo("access.token");
            verify(mfaService).revokeMfaPendingSession("mfa-session-token");
            verify(securityAuditLogger).logMfaSuccess("testuser", "unknown");
            verify(securityAuditLogger).logLoginSuccess("testuser", "unknown");
        }

        @Test
        @DisplayName("Should verify EMAIL OTP successfully and return tokens")
        void shouldVerifyEmailOtpSuccessfully() {
            User user = createTestUser();
            user.setMfaEnabled(true);
            user.setMfaMethod(MfaMethod.EMAIL);

            MfaVerifyRequest request = MfaVerifyRequest.builder()
                    .mfaSessionToken("mfa-session-token")
                    .code("123456")
                    .build();

            when(mfaService.validateMfaPendingSession("mfa-session-token")).thenReturn("testuser");
            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(mfaService.verifyEmailOtp("testuser", "123456")).thenReturn(true);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("testuser")
                    .password(ENCODED_PASSWORD)
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
            when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access.token");
            when(jwtTokenProvider.generateRefreshToken(any())).thenReturn(VALID_TOKEN);

            TokenResponse response = authService.verifyMfa(request);

            assertThat(response.getAccessToken()).isEqualTo("access.token");
            verify(mfaService).revokeMfaPendingSession("mfa-session-token");
        }

        @Test
        @DisplayName("Should throw BadCredentialsException for invalid MFA session token")
        void shouldThrowForInvalidMfaSessionToken() {
            MfaVerifyRequest request = MfaVerifyRequest.builder()
                    .mfaSessionToken("invalid-token")
                    .code("123456")
                    .build();

            when(mfaService.validateMfaPendingSession("invalid-token")).thenReturn(null);

            assertThatThrownBy(() -> authService.verifyMfa(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid or expired MFA session token");
        }

        @Test
        @DisplayName("Should throw BadCredentialsException for invalid TOTP code")
        void shouldThrowForInvalidTotpCode() {
            User user = createTestUser();
            user.setMfaEnabled(true);
            user.setMfaMethod(MfaMethod.TOTP);
            user.setMfaSecret("JBSWY3DPEHPK3PXP");

            MfaVerifyRequest request = MfaVerifyRequest.builder()
                    .mfaSessionToken("mfa-session-token")
                    .code("wrong-code")
                    .build();

            when(mfaService.validateMfaPendingSession("mfa-session-token")).thenReturn("testuser");
            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(mfaService.verifyTotpCode("JBSWY3DPEHPK3PXP", "wrong-code")).thenReturn(false);

            assertThatThrownBy(() -> authService.verifyMfa(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid MFA code");
            verify(securityAuditLogger).logMfaFailure("testuser", "unknown", "Invalid MFA code");
        }
    }

    @Nested
    @DisplayName("MFA Enable/Disable Tests")
    class MfaEnableDisableTests {

        @Test
        @DisplayName("Should enable TOTP MFA and return QR URI")
        void shouldEnableTotpMfa() {
            User user = createTestUser();
            Authentication auth = createAuthentication(user);

            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(mfaService.generateTotpSecret()).thenReturn("JBSWY3DPEHPK3PXP");
            when(mfaService.generateOtpAuthUri("testuser", "JBSWY3DPEHPK3PXP"))
                    .thenReturn("otpauth://totp/testuser?secret=JBSWY3DPEHPK3PXP");

            MfaEnableRequest request = MfaEnableRequest.builder()
                    .method(MfaMethod.TOTP)
                    .build();

            MfaSetupResponse response = authService.enableMfa(auth, request);

            assertThat(response.getQrUri()).isEqualTo("otpauth://totp/testuser?secret=JBSWY3DPEHPK3PXP");
            assertThat(response.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
            assertThat(response.getMethod()).isEqualTo("TOTP");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Should enable EMAIL MFA and send OTP")
        void shouldEnableEmailMfa() {
            User user = createTestUser();
            Authentication auth = createAuthentication(user);

            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(mfaService.generateEmailOtp()).thenReturn("123456");

            MfaEnableRequest request = MfaEnableRequest.builder()
                    .method(MfaMethod.EMAIL)
                    .build();

            MfaSetupResponse response = authService.enableMfa(auth, request);

            assertThat(response.getMethod()).isEqualTo("EMAIL");
            verify(mfaService).storeEmailOtp("testuser", "123456");
            verify(emailService).sendMfaCodeEmail("test@example.com", "123456");
        }

        @Test
        @DisplayName("Should disable MFA with correct password")
        void shouldDisableMfaWithCorrectPassword() {
            User user = createTestUser();
            user.setMfaEnabled(true);
            user.setMfaMethod(MfaMethod.TOTP);
            user.setMfaSecret("JBSWY3DPEHPK3PXP");
            Authentication auth = createAuthentication(user);

            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(passwordEncoder.matches("password", ENCODED_PASSWORD)).thenReturn(true);

            MfaDisableRequest request = MfaDisableRequest.builder()
                    .password("password")
                    .build();

            authService.disableMfa(auth, request);

            assertThat(user.getMfaEnabled()).isFalse();
            assertThat(user.getMfaMethod()).isEqualTo(MfaMethod.NONE);
            assertThat(user.getMfaSecret()).isNull();
            verify(securityAuditLogger).logMfaDisabled("testuser", "unknown");
        }

        @Test
        @DisplayName("Should throw BadCredentialsException when disabling MFA with wrong password")
        void shouldThrowWhenDisablingMfaWithWrongPassword() {
            User user = createTestUser();
            user.setMfaEnabled(true);
            Authentication auth = createAuthentication(user);

            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);
            when(passwordEncoder.matches("wrongpassword", ENCODED_PASSWORD)).thenReturn(false);

            MfaDisableRequest request = MfaDisableRequest.builder()
                    .password("wrongpassword")
                    .build();

            assertThatThrownBy(() -> authService.disableMfa(auth, request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Current password is incorrect");
        }

        @Test
        @DisplayName("Should return MFA status")
        void shouldReturnMfaStatus() {
            User user = createTestUser();
            user.setMfaEnabled(true);
            user.setMfaMethod(MfaMethod.TOTP);
            Authentication auth = createAuthentication(user);

            when(userDetailsService.loadUserEntityByUsername("testuser")).thenReturn(user);

            MfaStatusResponse response = authService.getMfaStatus(auth);

            assertThat(response.isMfaEnabled()).isTrue();
            assertThat(response.getMethod()).isEqualTo(MfaMethod.TOTP);
        }
    }
}
