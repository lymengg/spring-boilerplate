package com.example.demo.security.filter;

import com.example.demo.config.AppProperties;
import com.example.demo.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF defense for cookie-based browser flows (the app is served from a
 * different origin than the API, e.g. frontend at https://xxx.com and API at
 * https://api.xxx.com).
 *
 * `SameSite=Strict` blocks cross-SITE requests, but the frontend and API share
 * a registrable domain (same site), so a malicious sibling application on that
 * domain would still send the Strict cookies. For any state-changing method we
 * therefore require the `Origin` header to exactly match the configured
 * frontend origin. Browsers always send `Origin` on state-changing requests;
 * requests without it (API clients using bearer tokens) are allowed — CSRF
 * only applies to cookie-authenticated requests.
 *
 * Safe methods (GET, HEAD, OPTIONS) are exempt — they must not cause state
 * changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            // Non-browser client (bearer-token auth) — CSRF does not apply.
            filterChain.doFilter(request, response);
            return;
        }

        if (!appProperties.getFrontendUrl().equals(origin)) {
            log.warn("Cross-origin request blocked: method={} origin={}", request.getMethod(), origin);
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper
                    .writeValueAsString(ApiResponse.error("Cross-origin request blocked")));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
