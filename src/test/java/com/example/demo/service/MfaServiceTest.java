package com.example.demo.service;

import com.example.demo.config.MfaProperties;
import com.example.demo.security.service.TokenHashingService;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaServiceTest {

    @Mock
    private MfaProperties mfaProperties;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TokenHashingService tokenHashingService;

    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        when(mfaProperties.getIssuer()).thenReturn("test-issuer");
        when(mfaProperties.getOtpExpirationSeconds()).thenReturn(300);
        when(mfaProperties.getPendingTokenExpiration()).thenReturn(300000L);
        when(mfaProperties.getOtpDigits()).thenReturn(6);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        mfaService = new MfaService(mfaProperties, redisTemplate, tokenHashingService);
    }

    @Test
    @DisplayName("Should generate a non-empty TOTP secret")
    void shouldGenerateTotpSecret() {
        String secret = mfaService.generateTotpSecret();

        assertThat(secret).isNotNull();
        assertThat(secret).isNotEmpty();
    }

    @Test
    @DisplayName("Should generate otpauth URI with correct format")
    void shouldGenerateOtpAuthUri() {
        String uri = mfaService.generateOtpAuthUri("testuser", "JBSWY3DPEHPK3PXP");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(uri).contains("issuer=test-issuer");
    }

    @Test
    @DisplayName("Should verify valid TOTP code")
    void shouldVerifyValidTotpCode() {
        String secret = mfaService.generateTotpSecret();
        dev.samstevens.totp.code.DefaultCodeGenerator codeGenerator = new dev.samstevens.totp.code.DefaultCodeGenerator();
        try {
            String code = codeGenerator.generate(secret, Math.floorDiv(System.currentTimeMillis() / 1000, 30));
            boolean result = mfaService.verifyTotpCode(secret, code);
            assertThat(result).isTrue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should reject invalid TOTP code")
    void shouldRejectInvalidTotpCode() {
        boolean result = mfaService.verifyTotpCode("JBSWY3DPEHPK3PXP", "000000");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should generate 6-digit email OTP")
    void shouldGenerateEmailOtp() {
        String otp = mfaService.generateEmailOtp();

        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");
    }

    @Test
    @DisplayName("Should store email OTP in Redis with TTL")
    void shouldStoreEmailOtp() {
        mfaService.storeEmailOtp("testuser", "123456");

        verify(valueOperations).set(eq("mfa_otp:testuser"), eq("123456"), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("Should verify correct email OTP and delete from Redis")
    void shouldVerifyCorrectEmailOtp() {
        when(valueOperations.get("mfa_otp:testuser")).thenReturn("123456");

        boolean result = mfaService.verifyEmailOtp("testuser", "123456");

        assertThat(result).isTrue();
        verify(redisTemplate).delete("mfa_otp:testuser");
    }

    @Test
    @DisplayName("Should reject incorrect email OTP")
    void shouldRejectIncorrectEmailOtp() {
        when(valueOperations.get("mfa_otp:testuser")).thenReturn("123456");

        boolean result = mfaService.verifyEmailOtp("testuser", "999999");

        assertThat(result).isFalse();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Should reject email OTP when not found in Redis")
    void shouldRejectEmailOtpWhenNotFound() {
        when(valueOperations.get("mfa_otp:testuser")).thenReturn(null);

        boolean result = mfaService.verifyEmailOtp("testuser", "123456");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should store MFA pending session and return token")
    void shouldStoreMfaPendingSession() {
        when(tokenHashingService.generateSecureToken()).thenReturn("session-token-123");

        String token = mfaService.storeMfaPendingSession("testuser");

        assertThat(token).isEqualTo("session-token-123");
        verify(valueOperations).set(eq("mfa_pending:session-token-123"), eq("testuser"), eq(Duration.ofMillis(300000L)));
    }

    @Test
    @DisplayName("Should validate MFA pending session and return username")
    void shouldValidateMfaPendingSession() {
        when(valueOperations.get("mfa_pending:session-token-123")).thenReturn("testuser");

        String username = mfaService.validateMfaPendingSession("session-token-123");

        assertThat(username).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should return null for invalid MFA pending session")
    void shouldReturnNullForInvalidMfaPendingSession() {
        when(valueOperations.get("mfa_pending:invalid-token")).thenReturn(null);

        String username = mfaService.validateMfaPendingSession("invalid-token");

        assertThat(username).isNull();
    }

    @Test
    @DisplayName("Should revoke MFA pending session")
    void shouldRevokeMfaPendingSession() {
        mfaService.revokeMfaPendingSession("session-token-123");

        verify(redisTemplate).delete("mfa_pending:session-token-123");
    }
}
