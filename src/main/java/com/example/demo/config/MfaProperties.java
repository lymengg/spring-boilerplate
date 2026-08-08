package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mfa")
public class MfaProperties {

    private String issuer = "security-boilerplate";
    private int otpExpirationSeconds = 300;
    private long pendingTokenExpiration = 300000;
    private int otpDigits = 6;
}
