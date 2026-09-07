package com.example.demo.security.cookie;

import com.example.demo.config.CookieProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

class CookieFactoryTest {

    private final CookieProperties prodProps = props(true, "Strict", "/");
    private final CookieProperties devProps = props(false, "Lax", "/");

    @Test
    @DisplayName("Prod cookie carries HttpOnly, Secure, SameSite, Path and max-age")
    void prodCookieCarriesSecurityAttributes() {
        ResponseCookie cookie = CookieFactory.create("access_token", "value", 900, prodProps);

        assertThat(cookie.getName()).isEqualTo("access_token");
        assertThat(cookie.getValue()).isEqualTo("value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("Dev cookie drops Secure when configured for plain HTTP")
    void devCookieDropsSecure() {
        ResponseCookie cookie = CookieFactory.create("access_token", "value", 900, devProps);

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    @DisplayName("Cookie carries no Domain attribute so it stays host-only")
    void cookieHasNoDomainAttribute() {
        ResponseCookie cookie = CookieFactory.create("access_token", "value", 900, prodProps);

        assertThat(cookie.toString()).doesNotContain("Domain=");
    }

    private CookieProperties props(boolean secure, String sameSite, String path) {
        CookieProperties props = new CookieProperties();
        props.setSecure(secure);
        props.setSameSite(sameSite);
        props.setPath(path);
        return props;
    }
}
