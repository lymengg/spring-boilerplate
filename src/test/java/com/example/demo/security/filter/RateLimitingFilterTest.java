package com.example.demo.security.filter;

import com.example.demo.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitingFilterTest {

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    @Mock
    private FilterChain filterChain;

    @Mock
    private PrintWriter printWriter;

    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        SecurityProperties.RateLimiting rateLimiting = new SecurityProperties.RateLimiting();
        rateLimiting.setMaxLoginRequests(5);
        rateLimiting.setWindowMillis(60_000);

        when(securityProperties.getRateLimiting()).thenReturn(rateLimiting);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        rateLimitingFilter = new RateLimitingFilter(redisTemplate, securityProperties);
    }

    @Test
    @DisplayName("Should allow requests when under rate limit")
    void shouldAllowRequestsUnderRateLimit() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/auth/login");
        when(valueOperations.increment("rate_limit:127.0.0.1")).thenReturn(1L);

        rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(filterChain).doFilter(httpRequest, httpResponse);
        verify(httpResponse, never()).setStatus(429);
    }

    @Test
    @DisplayName("Should block requests when rate limit exceeded")
    void shouldBlockRequestsWhenRateLimitExceeded() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/auth/login");
        when(httpResponse.getWriter()).thenReturn(printWriter);
        when(valueOperations.increment("rate_limit:127.0.0.1"))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        for (int i = 0; i < 5; i++) {
            rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);
        }

        verify(filterChain, times(5)).doFilter(httpRequest, httpResponse);

        rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).setStatus(429);
        verify(printWriter).write(anyString());
        verify(filterChain, times(5)).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should not apply rate limiting to non-auth endpoints")
    void shouldNotApplyRateLimitingToNonAuthEndpoints() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/users");

        for (int i = 0; i < 10; i++) {
            rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);
        }

        verify(filterChain, times(10)).doFilter(httpRequest, httpResponse);
        verify(httpResponse, never()).setStatus(429);
    }

    @Test
    @DisplayName("Should respect X-Forwarded-For header for client IP")
    void shouldRespectForwardedForHeader() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/auth/login");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 192.168.1.1");
        when(valueOperations.increment("rate_limit:10.0.0.1")).thenReturn(1L);

        rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(filterChain).doFilter(httpRequest, httpResponse);
    }

    @Test
    @DisplayName("Should apply rate limiting to register endpoint")
    void shouldApplyRateLimitingToRegisterEndpoint() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/auth/register");
        when(httpResponse.getWriter()).thenReturn(printWriter);
        when(valueOperations.increment("rate_limit:127.0.0.1"))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        for (int i = 0; i < 5; i++) {
            rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);
        }

        rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).setStatus(429);
    }

    @Test
    @DisplayName("Should apply rate limiting to refresh endpoint")
    void shouldApplyRateLimitingToRefreshEndpoint() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/auth/refresh");
        when(httpResponse.getWriter()).thenReturn(printWriter);
        when(valueOperations.increment("rate_limit:127.0.0.1"))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        for (int i = 0; i < 5; i++) {
            rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);
        }

        rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).setStatus(429);
    }

    @Test
    @DisplayName("Should track rate limit per IP independently")
    void shouldTrackRateLimitPerIpIndependently() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/auth/login");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(valueOperations.increment("rate_limit:192.168.1.100"))
                .thenReturn(1L, 2L, 3L, 4L, 5L);

        for (int i = 0; i < 5; i++) {
            rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);
        }

        verify(filterChain, times(5)).doFilter(httpRequest, httpResponse);

        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.200");
        when(valueOperations.increment("rate_limit:192.168.1.200")).thenReturn(1L);

        rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(filterChain, times(6)).doFilter(httpRequest, httpResponse);
        verify(httpResponse, never()).setStatus(429);
    }

    @Test
    @DisplayName("Should set TTL on first request")
    void shouldSetTtlOnFirstRequest() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/auth/login");
        when(valueOperations.increment("rate_limit:127.0.0.1")).thenReturn(1L);

        rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(redisTemplate).expire(eq("rate_limit:127.0.0.1"), any(Duration.class));
    }

    @Test
    @DisplayName("Should not set TTL on subsequent requests")
    void shouldNotSetTtlOnSubsequentRequests() throws ServletException, IOException {
        when(httpRequest.getRequestURI()).thenReturn("/api/auth/login");
        when(valueOperations.increment("rate_limit:127.0.0.1")).thenReturn(2L);

        rateLimitingFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }
}
