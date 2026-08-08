package com.example.demo.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TokenHashingServiceTest {

    private TokenHashingService tokenHashingService;

    @BeforeEach
    void setUp() {
        tokenHashingService = new TokenHashingService();
    }

    @Test
    @DisplayName("Should hash token to consistent SHA-256 hex string")
    void shouldHashTokenConsistently() {
        String token = "test-token-123";

        String hash1 = tokenHashingService.hashToken(token);
        String hash2 = tokenHashingService.hashToken(token);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    @DisplayName("Should produce different hashes for different tokens")
    void shouldProduceDifferentHashesForDifferentTokens() {
        String hash1 = tokenHashingService.hashToken("token1");
        String hash2 = tokenHashingService.hashToken("token2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should generate non-empty secure token")
    void shouldGenerateSecureToken() {
        String token = tokenHashingService.generateSecureToken();

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token).hasSize(64);
    }

    @Test
    @DisplayName("Should generate unique tokens")
    void shouldGenerateUniqueTokens() {
        String token1 = tokenHashingService.generateSecureToken();
        String token2 = tokenHashingService.generateSecureToken();

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("Should throw IllegalStateException for null token in hashToken")
    void shouldHandleNullToken() {
        assertThatThrownBy(() -> tokenHashingService.hashToken(null))
                .isInstanceOf(NullPointerException.class);
    }
}
