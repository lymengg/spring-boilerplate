package com.example.demo.security.jwt;

import com.example.demo.config.JwtConfig;
import com.example.demo.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenProviderTest {

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private CustomUserDetailsService userDetailsService;

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET_KEY = "this-is-a-very-secure-secret-key-for-testing-minimum-length-512-bits-for-HS512!";

    @BeforeEach
    void setUp() {
        when(jwtConfig.getSecret()).thenReturn(SECRET_KEY);
        when(jwtConfig.getIssuer()).thenReturn("test-issuer");
        when(jwtConfig.getAudience()).thenReturn("test-audience");
        when(jwtConfig.getAccessTokenExpiration()).thenReturn(900000L);
        when(jwtConfig.getRefreshTokenExpiration()).thenReturn(604800000L);

        jwtTokenProvider = new JwtTokenProvider(jwtConfig, userDetailsService);
    }

    private Authentication createAuthentication() {
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("testuser")
                .password("encoded")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    @DisplayName("Should generate access token with correct claims")
    void shouldGenerateAccessTokenWithCorrectClaims() {
        Authentication auth = createAuthentication();

        String token = jwtTokenProvider.generateAccessToken(auth);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should generate refresh token with type=refresh claim")
    void shouldGenerateRefreshTokenWithTypeClaim() {
        Authentication auth = createAuthentication();

        String token = jwtTokenProvider.generateRefreshToken(auth);

        assertThat(token).isNotNull();
        assertThat(jwtTokenProvider.validateRefreshToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should validate access token successfully")
    void shouldValidateAccessTokenSuccessfully() {
        Authentication auth = createAuthentication();
        String token = jwtTokenProvider.generateAccessToken(auth);

        assertThat(jwtTokenProvider.validateAccessToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should reject refresh token as access token")
    void shouldRejectRefreshTokenAsAccessToken() {
        Authentication auth = createAuthentication();
        String refreshToken = jwtTokenProvider.generateRefreshToken(auth);

        assertThat(jwtTokenProvider.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("Should validate refresh token successfully")
    void shouldValidateRefreshTokenSuccessfully() {
        Authentication auth = createAuthentication();
        String token = jwtTokenProvider.generateRefreshToken(auth);

        assertThat(jwtTokenProvider.validateRefreshToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should reject access token as refresh token")
    void shouldRejectAccessTokenAsRefreshToken() {
        Authentication auth = createAuthentication();
        String accessToken = jwtTokenProvider.generateAccessToken(auth);

        assertThat(jwtTokenProvider.validateRefreshToken(accessToken)).isFalse();
    }

    @Test
    @DisplayName("Should return false for invalid token")
    void shouldReturnFalseForInvalidToken() {
        assertThat(jwtTokenProvider.validateAccessToken("invalid.token.string")).isFalse();
        assertThat(jwtTokenProvider.validateRefreshToken("invalid.token.string")).isFalse();
    }

    @Test
    @DisplayName("Should extract username from token")
    void shouldExtractUsernameFromToken() {
        Authentication auth = createAuthentication();
        String token = jwtTokenProvider.generateAccessToken(auth);

        String username = jwtTokenProvider.getUsernameFromToken(token);

        assertThat(username).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should extract roles from token")
    void shouldExtractRolesFromToken() {
        Authentication auth = createAuthentication();
        String token = jwtTokenProvider.generateAccessToken(auth);

        var roles = jwtTokenProvider.getRolesFromToken(token);

        assertThat(roles).contains("ROLE_USER");
    }

    @Test
    @DisplayName("Should return null for userId when UserDetails is not User entity")
    void shouldExtractUserIdFromToken() {
        Authentication auth = createAuthentication();
        String token = jwtTokenProvider.generateAccessToken(auth);

        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        assertThat(userId).isNull();
    }
}
