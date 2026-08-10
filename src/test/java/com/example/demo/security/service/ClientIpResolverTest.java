package com.example.demo.security.service;

import com.example.demo.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientIpResolverTest {

    @Mock
    private HttpServletRequest request;

    private SecurityProperties securityProperties;
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
    }

    @Test
    @DisplayName("Should return remote address when no trusted proxies configured")
    void shouldReturnRemoteAddrWhenNoTrustedProxies() {
        clientIpResolver = new ClientIpResolver(securityProperties);
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("Should ignore X-Forwarded-For when remote address is not a trusted proxy")
    void shouldIgnoreForwardedForWhenRemoteNotTrusted() {
        securityProperties.getRateLimiting().setTrustedProxies(List.of("127.0.0.1"));
        clientIpResolver = new ClientIpResolver(securityProperties);

        when(request.getRemoteAddr()).thenReturn("203.0.113.5");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For when behind trusted proxy")
    void shouldExtractClientIpFromForwardedFor() {
        securityProperties.getRateLimiting().setTrustedProxies(List.of("127.0.0.1"));
        clientIpResolver = new ClientIpResolver(securityProperties);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.10");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("198.51.100.10");
    }

    @Test
    @DisplayName("Should skip trusted IPs when parsing from right to left")
    void shouldSkipTrustedIpsWhenParsing() {
        securityProperties.getRateLimiting().setTrustedProxies(List.of("127.0.0.1", "10.0.0.0/8"));
        clientIpResolver = new ClientIpResolver(securityProperties);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.10");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("Should support CIDR trusted proxy entries")
    void shouldSupportCidrTrustedProxyEntries() {
        securityProperties.getRateLimiting().setTrustedProxies(List.of("172.16.0.0/12"));
        clientIpResolver = new ClientIpResolver(securityProperties);

        when(request.getRemoteAddr()).thenReturn("172.18.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("Should fall back to remote address when X-Forwarded-For is absent")
    void shouldFallbackWhenForwardedForAbsent() {
        securityProperties.getRateLimiting().setTrustedProxies(List.of("127.0.0.1"));
        clientIpResolver = new ClientIpResolver(securityProperties);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Should return remote address for empty X-Forwarded-For")
    void shouldReturnRemoteAddrForEmptyForwardedFor() {
        securityProperties.getRateLimiting().setTrustedProxies(List.of("127.0.0.1"));
        clientIpResolver = new ClientIpResolver(securityProperties);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("127.0.0.1");
    }
}
