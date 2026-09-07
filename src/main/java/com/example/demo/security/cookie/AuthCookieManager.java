package com.example.demo.security.cookie;

import com.example.demo.config.CookieProperties;
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
 * Knows which cookies exist and how long they live (TTLs from
 * {@link JwtConfig}); the security attributes (HttpOnly, Secure, SameSite,
 * Path) come from {@link CookieFactory} + {@link CookieProperties}.
 *
 * This component is the only place allowed to construct these cookies — the
 * controller (write) and the authentication filter (read) both go through it.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieManager {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final JwtConfig jwtConfig;
    private final CookieProperties cookieProperties;

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
        ResponseCookie cookie = CookieFactory.create(name, value, maxAgeSeconds, cookieProperties);
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = CookieFactory.create(name, "", 0, cookieProperties);
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
