package com.example.demo.security.cookie;

import com.example.demo.config.JwtConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    private AuthCookieService authCookieService;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("test-secret-at-least-32-chars-long-for-hs512-unit-test");
        jwtConfig.setAccessTokenExpiration(900_000L);   // 15 min
        jwtConfig.setRefreshTokenExpiration(604_800_000L); // 7 days
        authCookieService = new AuthCookieService(jwtConfig);
    }

    @Test
    @DisplayName("Add cookies sets httpOnly, Secure, SameSite=Strict, Path=/ and TTL-matched max-age")
    void addCookiesSetsSecureAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.addCookies(response, "access-token-value", "refresh-token-value");

        assertThat(response.getHeaderValues("Set-Cookie")).hasSize(2);

        Cookie access = response.getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        assertThat(access).isNotNull();
        assertThat(access.getValue()).isEqualTo("access-token-value");
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getSecure()).isTrue();
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(access.getMaxAge()).isEqualTo(900);

        Cookie refresh = response.getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        assertThat(refresh).isNotNull();
        assertThat(refresh.getValue()).isEqualTo("refresh-token-value");
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getSecure()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/");
        assertThat(refresh.getMaxAge()).isEqualTo(604_800);

        // SameSite is not exposed on jakarta Cookie — assert on the raw header.
        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Strict");
    }

    @Test
    @DisplayName("Cookies carry no Domain attribute so they stay host-only (scoped to the BFF origin)")
    void cookiesHaveNoDomainAttribute() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.addCookies(response, "a", "r");

        assertThat(response.getHeader("Set-Cookie")).doesNotContain("Domain=");
    }

    @Test
    @DisplayName("Clear cookies expires both cookies")
    void clearCookiesExpiresBoth() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.clearCookies(response);

        assertThat(response.getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE).getMaxAge()).isZero();
        assertThat(response.getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE).getMaxAge()).isZero();
    }

    @Test
    @DisplayName("Resolve reads tokens back from request cookies")
    void resolveReadsRequestCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "a-value"),
                new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "r-value")
        );

        assertThat(authCookieService.resolveAccessToken(request)).isEqualTo("a-value");
        assertThat(authCookieService.resolveRefreshToken(request)).isEqualTo("r-value");
    }

    @Test
    @DisplayName("Resolve returns null when cookies are absent")
    void resolveReturnsNullWithoutCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(authCookieService.resolveAccessToken(request)).isNull();
        assertThat(authCookieService.resolveRefreshToken(request)).isNull();
    }
}
