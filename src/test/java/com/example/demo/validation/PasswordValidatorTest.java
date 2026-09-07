package com.example.demo.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidatorTest {

    private PasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordValidator();
    }

    @Test
    @DisplayName("Accepts a password meeting all complexity rules")
    void acceptsValidPassword() {
        assertThat(validator.isValid("Password123!", null)).isTrue();
    }

    @Test
    @DisplayName("Rejects a password shorter than 8 characters")
    void rejectsTooShortPassword() {
        assertThat(validator.isValid("Pass12!", null)).isFalse();
    }

    @Test
    @DisplayName("Rejects a password without a lowercase letter")
    void rejectsMissingLowercase() {
        assertThat(validator.isValid("PASSWORD123!", null)).isFalse();
    }

    @Test
    @DisplayName("Rejects a password without an uppercase letter")
    void rejectsMissingUppercase() {
        assertThat(validator.isValid("password123!", null)).isFalse();
    }

    @Test
    @DisplayName("Rejects a password without a digit")
    void rejectsMissingDigit() {
        assertThat(validator.isValid("Passwordabc!", null)).isFalse();
    }

    @Test
    @DisplayName("Rejects a password without a special character")
    void rejectsMissingSpecialCharacter() {
        assertThat(validator.isValid("Password123", null)).isFalse();
    }

    @Test
    @DisplayName("Rejects a null password")
    void rejectsNullPassword() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
