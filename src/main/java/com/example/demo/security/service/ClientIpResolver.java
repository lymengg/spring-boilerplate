package com.example.demo.security.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the real client IP for rate limiting / lockout keying.
 *
 * Clients cannot reach the backend directly: the reverse proxy (TLS
 * terminator / CDN) is the only entry point and overwrites X-Forwarded-For
 * with the real client IP, so the header is trusted unconditionally. The
 * first value is the original client; remaining values are proxies in the
 * chain.
 */
@Component
public class ClientIpResolver {

    public String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
