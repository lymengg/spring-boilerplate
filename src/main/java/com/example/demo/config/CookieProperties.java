package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cookie security attributes, environment-aware.
 *
 * Defaults are prod-safe (Secure on). The dev profile overrides
 * {@code app.cookie.secure=false} so cookies still work over plain HTTP
 * when running via a non-localhost IP.
 */
@Configuration
@ConfigurationProperties(prefix = "app.cookie")
@Getter
@Setter
public class CookieProperties {

    private boolean secure = true;
    private String sameSite = "Strict";
    private String path = "/";
}
