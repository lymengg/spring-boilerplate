package com.example.demo.security.filter;

import com.example.demo.config.SecurityProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@Component
@Order(1)
@Slf4j
public class RateLimitingFilter implements Filter {

    private final RedisTemplate<String, String> redisTemplate;
    private final SecurityProperties securityProperties;
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/refresh",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/change-password",
        "/api/auth/mfa/verify"
    );

    public RateLimitingFilter(RedisTemplate<String, String> redisTemplate, SecurityProperties securityProperties) {
        this.redisTemplate = redisTemplate;
        this.securityProperties = securityProperties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (RATE_LIMITED_PATHS.contains(path)) {
            String clientIp = getClientIp(httpRequest);
            String key = RATE_LIMIT_PREFIX + clientIp;

            if (!tryConsume(key)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Too many requests\",\"message\":\"Please try again later\"}");
                log.warn("Rate limit exceeded for IP: {}", clientIp);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean tryConsume(String key) {
        long windowMillis = securityProperties.getRateLimiting().getWindowMillis();
        int maxRequests = securityProperties.getRateLimiting().getMaxLoginRequests();

        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMillis(windowMillis));
        }

        return count != null && count <= maxRequests;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
