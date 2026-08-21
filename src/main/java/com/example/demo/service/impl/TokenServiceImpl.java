package com.example.demo.service.impl;

import com.example.demo.config.JwtConfig;
import com.example.demo.dto.TokenResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.example.demo.security.service.RefreshTokenService;
import com.example.demo.security.service.TokenBlacklistService;
import com.example.demo.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Single owner of the JWT lifecycle (creation, refresh, revocation). Centralizing
 * token operations prevents token leaks between auth flows and ensures refresh
 * token rotation is always applied.
 */
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final RefreshTokenService refreshTokenService;
    private final CustomUserDetailsService customUserDetailsService;
    private final SecurityAuditLogger securityAuditLogger;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Reuses Spring Security UserDetails to build the authentication object, so
     * token claims always match the actual granted authorities.
     */
    @Override
    @Transactional
    public TokenResponse generateTokenResponse(User user) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        refreshTokenService.revokeAllUserRefreshTokens(user.getUsername());
        refreshTokenService.storeRefreshToken(user.getUsername(), refreshToken, jwtConfig.getRefreshTokenExpiration());

        return buildTokenResponse(accessToken, refreshToken, user);
    }

    /**
     * Validates the refresh token, then revokes the old one before issuing a new
     * one. This rotation mitigates refresh-token replay attacks.
     */
    @Override
    @Transactional
    public TokenResponse refreshToken(String refreshToken, String ipAddress) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        if (!refreshTokenService.validateRefreshToken(username, refreshToken)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Refresh token not found or revoked");
        }

        Authentication authentication = jwtTokenProvider.getAuthentication(refreshToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        refreshTokenService.revokeRefreshToken(refreshToken);
        refreshTokenService.storeRefreshToken(username, newRefreshToken, jwtConfig.getRefreshTokenExpiration());

        User user = customUserDetailsService.loadUserEntityByUsername(username);
        securityAuditLogger.logTokenRefreshed(username, ipAddress);

        return buildTokenResponse(newAccessToken, newRefreshToken, user);
    }

    /**
     * Revokes the current access token (via blacklist) and all refresh tokens for the user,
     * terminating any active sessions.
     */
    @Override
    @Transactional
    public void logout(String username, String accessToken, String ipAddress) {
        if (accessToken != null) {
            try {
                String jti = jwtTokenProvider.getIdFromToken(accessToken);
                long remainingMs = jwtTokenProvider.getRemainingExpiration(accessToken);
                if (remainingMs > 0) {
                    tokenBlacklistService.blacklistToken(jti, Duration.ofMillis(remainingMs));
                }
            } catch (Exception e) {
                // Token may already be expired or malformed; continue with refresh token revocation
            }
        }

        refreshTokenService.revokeAllUserRefreshTokens(username);
        securityAuditLogger.logLogout(username, ipAddress);
    }

    /**
     * Delegated from the auth orchestrator so password changes can revoke tokens
     * without duplicating the refresh-token repository logic.
     */
    @Override
    public void revokeAllUserRefreshTokens(String username) {
        refreshTokenService.revokeAllUserRefreshTokens(username);
    }

    /**
     * Maps the user entity into the public token response, keeping DTO conversion
     * inside the token layer instead of leaking it to the orchestrator.
     */
    private TokenResponse buildTokenResponse(String accessToken, String refreshToken, User user) {
        String[] roles = user.getRoles().stream()
                .map(Role::getName)
                .toArray(String[]::new);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtConfig.getAccessTokenExpiration() / 1000)
                .username(user.getUsername())
                .roles(roles)
                .build();
    }
}
