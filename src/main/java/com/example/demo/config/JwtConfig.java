package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
@ToString(exclude = "secret")
@Slf4j
public class JwtConfig {

    private String secret;
    private long accessTokenExpiration = 900000;
    private long refreshTokenExpiration = 604800000;
    /** How long a just-rotated refresh token stays valid, so parallel requests
     *  (multiple browser tabs) at the rotation boundary don't log each other
     *  out. See OAuth 2.0 BCP §4.14 (reuse grace period). Default 60s. */
    private long refreshTokenGraceWindow = 60000;
    private String issuer = "security-boilerplate";
    private String audience = "api.security-boilerplate";

    @PostConstruct
    public void init() {
        String envSecret = System.getenv("JWT_SECRET");
        if (StringUtils.hasText(envSecret)) {
            this.secret = envSecret;
        }
         if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 characters long. Set via JWT_SECRET environment variable or jwt.secret property."
            );
        }
        // HS512 requires at least 512 bits (64 bytes) key per RFC 7518
        long keyBytesLength = secret.getBytes(StandardCharsets.UTF_8).length;
        if (keyBytesLength < 64) {
            log.warn("JWT secret is {} bytes; HS512 recommends at least 64 bytes for optimal security", keyBytesLength);
        }
    }
}