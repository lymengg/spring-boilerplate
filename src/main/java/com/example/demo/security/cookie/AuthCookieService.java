package com.example.demo.security.cookie;

import com.example.demo.config.JwtConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Single owner of the httpOnly auth cookies (access + refresh tokens).
 *
 * Security contract (browser must never read tokens via JavaScript):
 * - httpOnly: prevents document.cookie / XSS from reading the tokens.
 * - Secure: only sent over HTTPS (modern browsers also accept on localhost dev).
 * - SameSite=Strict: not sent cross-site — primary CSRF defense.
 * - No Domain attribute: cookies are host-only, scoped to the origin that
 *   served them (the BFF/Nuxt origin). The browser therefore never sends
 *   these cookies to the backend directly — CSRF at the backend is
 *   structurally impossible, and the edge CSRF defense (SameSite + Origin
 *   check at the proxy) is the only one needed.
 * - `__Host-` name prefix (RFC 6265): the browser rejects any cookie with
 *   this prefix unless it is Secure, Path=/, and Domain-less — all enforced
 *   here. Prevents subdomain cookie injection and session fixation, and is
 *   recommended for client-side-session cookies (RFC 10017 §6.1.3.2).
 * - Path=/ and maxAge derived from the token TTLs in {@link JwtConfig} so the
 *   cookie lifetime always matches the token lifetime (single source of truth).
 *
 * This component is the only place allowed to construct these cookies — the
 * controller (write) and the authentication filter (read) both go through it.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "__Host-access_token";
    public static final String REFRESH_TOKEN_COOKIE = "__Host-refresh_token";

    private final JwtConfig jwtConfig;

    /** Sets both auth cookies (login, MFA verify, refresh). */
    public void addCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        addAccessTokenCookie(response, accessToken);
        addRefreshTokenCookie(response, refreshToken);
    }

    public void addAccessTokenCookie(HttpServletResponse response, String accessToken) {
        addCookie(response, ACCESS_TOKEN_COOKIE, accessToken,
                (int) (jwtConfig.getAccessTokenExpiration() / 1000));
    }

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken,
                (int) (jwtConfig.getRefreshTokenExpiration() / 1000));
    }

    /** Clears both auth cookies (logout, password change). */
    public void clearCookies(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE);
        clearCookie(response, REFRESH_TOKEN_COOKIE);
    }

    /** Returns the access token from the request cookie, or null. */
    public String resolveAccessToken(HttpServletRequest request) {
        return resolveCookie(request, ACCESS_TOKEN_COOKIE);
    }

    /** Returns the refresh token from the request cookie, or null. */
    public String resolveRefreshToken(HttpServletRequest request) {
        return resolveCookie(request, REFRESH_TOKEN_COOKIE);
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String resolveCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
