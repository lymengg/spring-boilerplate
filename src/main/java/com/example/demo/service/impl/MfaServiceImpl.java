package com.example.demo.service.impl;

import com.example.demo.config.MfaProperties;
import com.example.demo.security.service.TokenHashingService;
import com.example.demo.service.MfaService;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class MfaServiceImpl implements MfaService {

    private final MfaProperties mfaProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final TokenHashingService tokenHashingService;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    private static final String MFA_OTP_PREFIX = "mfa_otp:";
    private static final String MFA_PENDING_PREFIX = "mfa_pending:";

    @Override
    public String generateTotpSecret() {
        return secretGenerator.generate();
    }

    @Override
    public String generateQrUri(String username, String secret) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(mfaProperties.getIssuer())
                .algorithm(HashingAlgorithm.SHA1)
                .digits(mfaProperties.getOtpDigits())
                .period(30)
                .build();

        QrGenerator qrGenerator = new ZxingPngQrGenerator();
        try {
            byte[] qrImage = qrGenerator.generate(data);
            String base64 = java.util.Base64.getEncoder().encodeToString(qrImage);
            return "data:image/png;base64," + base64;
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code: {}", e.getMessage());
            return buildOtpAuthUri(username, secret);
        }
    }

    @Override
    public String generateOtpAuthUri(String username, String secret) {
        return buildOtpAuthUri(username, secret);
    }

    private String buildOtpAuthUri(String username, String secret) {
        String label = URLEncoder.encode(mfaProperties.getIssuer() + ":" + username, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=30",
                label, secret,
                URLEncoder.encode(mfaProperties.getIssuer(), StandardCharsets.UTF_8),
                mfaProperties.getOtpDigits());
    }

    @Override
    public boolean verifyTotpCode(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    @Override
    public String generateEmailOtp() {
        SecureRandom random = new SecureRandom();
        int digits = mfaProperties.getOtpDigits();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public void storeEmailOtp(String username, String code) {
        String key = MFA_OTP_PREFIX + username;
        redisTemplate.opsForValue().set(key, code, Duration.ofSeconds(mfaProperties.getOtpExpirationSeconds()));
        log.debug("Stored MFA email OTP for user: {}", username);
    }

    @Override
    public boolean verifyEmailOtp(String username, String code) {
        String key = MFA_OTP_PREFIX + username;
        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode != null && storedCode.equals(code)) {
            redisTemplate.delete(key);
            log.debug("Verified MFA email OTP for user: {}", username);
            return true;
        }
        return false;
    }

    @Override
    public String storeMfaPendingSession(String username) {
        String token = tokenHashingService.generateSecureToken();
        String key = MFA_PENDING_PREFIX + token;
        redisTemplate.opsForValue().set(key, username, Duration.ofMillis(mfaProperties.getPendingTokenExpiration()));
        log.debug("Stored MFA pending session for user: {}", username);
        return token;
    }

    @Override
    public String validateMfaPendingSession(String token) {
        String key = MFA_PENDING_PREFIX + token;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void revokeMfaPendingSession(String token) {
        String key = MFA_PENDING_PREFIX + token;
        redisTemplate.delete(key);
    }
}
