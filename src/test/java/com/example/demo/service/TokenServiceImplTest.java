package com.example.demo.service;

import com.example.demo.config.JwtConfig;
import com.example.demo.dto.TokenResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.example.demo.security.service.RefreshTokenService;
import com.example.demo.security.service.TokenBlacklistService;
import com.example.demo.service.impl.TokenServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtConfig jwtConfig;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CustomUserDetailsService customUserDetailsService;
    @Mock private SecurityAuditLogger securityAuditLogger;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private TokenServiceImpl tokenService;

    private Authentication authentication() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("testuser")
                .password("pass")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private User userEntity() {
        return User.builder()
                .username("testuser")
                .roles(Set.of(Role.builder().name("EMPLOYEE").build()))
                .build();
    }

    @Test
    @DisplayName("Refresh rotates the old token into a new pair with the grace window")
    void refreshRotatesToken() {
        when(jwtConfig.getRefreshTokenExpiration()).thenReturn(604_800_000L);
        when(jwtConfig.getRefreshTokenGraceWindow()).thenReturn(60_000L);
        when(jwtTokenProvider.validateRefreshToken("old-refresh")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("old-refresh")).thenReturn("testuser");
        when(refreshTokenService.validateRefreshToken("testuser", "old-refresh")).thenReturn(true);
        when(jwtTokenProvider.getAuthentication("old-refresh")).thenReturn(authentication());
        when(jwtTokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(any(Authentication.class))).thenReturn("new-refresh");
        when(customUserDetailsService.loadUserEntityByUsername("testuser")).thenReturn(userEntity());

        TokenResponse response = tokenService.refreshToken("old-refresh", "1.2.3.4");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).rotateRefreshToken(
                eq("testuser"), eq("old-refresh"), eq("new-refresh"),
                eq(604_800_000L), eq(60_000L));
        verify(securityAuditLogger).logTokenRefreshed("testuser", "1.2.3.4");
    }

    @Test
    @DisplayName("Refresh rejects a structurally invalid refresh token")
    void refreshRejectsInvalidJwt() {
        when(jwtTokenProvider.validateRefreshToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> tokenService.refreshToken("bad-token", "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenService, never()).rotateRefreshToken(any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Refresh rejects a revoked or unknown refresh token")
    void refreshRejectsRevokedToken() {
        when(jwtTokenProvider.validateRefreshToken("old-refresh")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("old-refresh")).thenReturn("testuser");
        when(refreshTokenService.validateRefreshToken("testuser", "old-refresh")).thenReturn(false);

        assertThatThrownBy(() -> tokenService.refreshToken("old-refresh", "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenService, never()).rotateRefreshToken(any(), any(), any(), anyLong(), anyLong());
    }
}
