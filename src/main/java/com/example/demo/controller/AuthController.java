package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final long REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7 days in seconds

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        Object loginResult = authService.login(request);

        if (loginResult instanceof TokenResponse tokenResponse) {
            addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
            return ResponseEntity.ok(ApiResponse.success("Login successful", sanitizeTokenResponse(tokenResponse)));
        }

        return ResponseEntity.ok(ApiResponse.success("Login successful", loginResult));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<TokenResponse>> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletResponse response) {
        TokenResponse tokenResponse = authService.verifyMfa(request);
        addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("MFA verification successful", sanitizeTokenResponse(tokenResponse)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Refresh token not found in cookie"));
        }

        TokenResponse tokenResponse = authService.refreshToken(refreshToken);
        addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", sanitizeTokenResponse(tokenResponse)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        String accessToken = extractAccessToken(request);
        authService.logout(authentication, accessToken);
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(Authentication authentication) {
        UserProfileResponse userProfileResponse = authService.getCurrentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(userProfileResponse));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletResponse response) {
        authService.changePassword(authentication, request);
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.<Void>success("If the email exists, a reset link has been sent", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.<Void>success("Password reset successfully", null));
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String extractAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Removes the refresh token from the response body since it's now in a cookie.
     */
    private TokenResponse sanitizeTokenResponse(TokenResponse tokenResponse) {
        return TokenResponse.builder()
                .accessToken(tokenResponse.getAccessToken())
                .tokenType(tokenResponse.getTokenType())
                .expiresIn(tokenResponse.getExpiresIn())
                .username(tokenResponse.getUsername())
                .roles(tokenResponse.getRoles())
                .build();
    }
}
