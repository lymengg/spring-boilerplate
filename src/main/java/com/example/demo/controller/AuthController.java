package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.security.cookie.AuthCookieService;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    /**
     * Login. Sets both auth cookies on success and returns the user profile.
     * Tokens are delivered exclusively via httpOnly cookies.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        LoginResult loginResult = authService.login(request);

        if (loginResult instanceof LoginResult.TokenSuccess tokenSuccess) {
            TokenResponse tokenResponse = tokenSuccess.tokenResponse();
            authCookieService.addCookies(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
            UserProfileResponse profile = authService.getUserProfile(tokenResponse.getUsername());
            return ResponseEntity.ok(ApiResponse.success("Login successful", profile));
        }

        LoginResult.MfaChallenge mfaChallenge = (LoginResult.MfaChallenge) loginResult;
        return ResponseEntity.ok(ApiResponse.success("Login successful", mfaChallenge.mfaResponse()));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<Object>> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletResponse response) {
        TokenResponse tokenResponse = authService.verifyMfa(request);
        authCookieService.addCookies(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
        UserProfileResponse profile = authService.getUserProfile(tokenResponse.getUsername());
        return ResponseEntity.ok(ApiResponse.success("MFA verification successful", profile));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Object>> refreshToken(
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        String refreshToken = authCookieService.resolveRefreshToken(servletRequest);
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Refresh token not provided"));
        }

        TokenResponse tokenResponse = authService.refreshToken(refreshToken);
        authCookieService.addCookies(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        String accessToken = authCookieService.resolveAccessToken(servletRequest);
        authService.logout(authentication, accessToken);
        authCookieService.clearCookies(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(Authentication authentication) {
        UserProfileResponse userProfileResponse = authService.getCurrentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved", userProfileResponse));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletResponse response) {
        authService.changePassword(authentication, request);
        authCookieService.clearCookies(response);
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

}
