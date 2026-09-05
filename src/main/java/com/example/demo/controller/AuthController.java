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
     * Login. Sets both auth cookies on success.
     * <p>
     * Response shaping:
     * - Browser flows (Origin header present): tokens are delivered ONLY via
     *   httpOnly cookies — the body carries the user profile, never tokens.
     * - API clients (no Origin): tokens are returned in the body (they do not
     *   hold browser cookies). The access token remains usable via the
     *   Authorization header for these clients.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        LoginResult loginResult = authService.login(request);

        if (loginResult instanceof LoginResult.TokenSuccess tokenSuccess) {
            TokenResponse tokenResponse = tokenSuccess.tokenResponse();
            authCookieService.addCookies(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());

            if (isBrowserFlow(servletRequest)) {
                UserProfileResponse profile = authService.getUserProfile(tokenResponse.getUsername());
                return ResponseEntity.ok(ApiResponse.success("Login successful", profile));
            }
            return ResponseEntity.ok(ApiResponse.success("Login successful", tokenResponse));
        }

        LoginResult.MfaChallenge mfaChallenge = (LoginResult.MfaChallenge) loginResult;
        return ResponseEntity.ok(ApiResponse.success("Login successful", mfaChallenge.mfaResponse()));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<Object>> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        TokenResponse tokenResponse = authService.verifyMfa(request);
        authCookieService.addCookies(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());

        if (isBrowserFlow(servletRequest)) {
            UserProfileResponse profile = authService.getUserProfile(tokenResponse.getUsername());
            return ResponseEntity.ok(ApiResponse.success("MFA verification successful", profile));
        }
        return ResponseEntity.ok(ApiResponse.success("MFA verification successful", tokenResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Object>> refreshToken(
            @Valid @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        // API clients send the refresh token in the body; browser flows rely on
        // the httpOnly cookie. Body takes precedence so API clients work cleanly.
        String refreshToken = body != null && body.getRefreshToken() != null
                ? body.getRefreshToken()
                : authCookieService.resolveRefreshToken(servletRequest);
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Refresh token not provided"));
        }

        TokenResponse tokenResponse = authService.refreshToken(refreshToken);
        authCookieService.addCookies(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());

        if (isBrowserFlow(servletRequest)) {
            // Browser flow: new tokens already delivered via cookies — no token in the body.
            return ResponseEntity.ok(ApiResponse.success("Token refreshed", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", tokenResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        String accessToken = extractAccessToken(servletRequest);
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

    /**
     * Browser flows are identified by the presence of an Origin header, which
     * browsers always send on state-changing requests. API clients (curl,
     * other services) do not send it. Stripping tokens for Origin-bearing
     * requests is fail-safe: an attacker forging Origin only loses token
     * access, never gains it.
     */
    private boolean isBrowserFlow(HttpServletRequest request) {
        return request.getHeader("Origin") != null;
    }

    private String extractAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // Browser flows send the access token as an httpOnly cookie.
        return authCookieService.resolveAccessToken(request);
    }

}
