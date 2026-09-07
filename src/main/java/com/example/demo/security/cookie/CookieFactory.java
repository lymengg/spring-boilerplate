package com.example.demo.security.cookie;

import com.example.demo.config.CookieProperties;
import org.springframework.http.ResponseCookie;

/**
 * Pure builder for cookies carrying the app-wide security contract
 * (HttpOnly, Secure, SameSite, Path). Static and dependency-free so every
 * cookie in the app inherits the same attributes and the contract is
 * testable in isolation.
 */
public final class CookieFactory {

    private CookieFactory() {
    }

    public static ResponseCookie create(String name, String value, int maxAgeSeconds, CookieProperties props) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path(props.getPath())
                .maxAge(maxAgeSeconds)
                .build();
    }
}
