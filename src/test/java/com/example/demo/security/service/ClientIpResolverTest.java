package com.example.demo.security.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientIpResolverTest {

    @Mock
    private HttpServletRequest request;

    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        clientIpResolver = new ClientIpResolver();
    }

    @Test
    @DisplayName("Should return remote address from request")
    void shouldReturnRemoteAddr() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("Should return localhost for local address")
    void shouldReturnLocalhost() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Should return IPv6 address")
    void shouldReturnIpv6Address() {
        when(request.getRemoteAddr()).thenReturn("::1");

        assertThat(clientIpResolver.resolveClientIp(request)).isEqualTo("::1");
    }
}
