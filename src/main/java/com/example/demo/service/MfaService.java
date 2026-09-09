package com.example.demo.service;

public interface MfaService {

    String generateTotpSecret();

    String generateQrUri(String email, String secret);

    String generateOtpAuthUri(String email, String secret);

    boolean verifyTotpCode(String secret, String code);

    String generateEmailOtp();

    void storeEmailOtp(String email, String code);

    boolean verifyEmailOtp(String email, String code);

    String storeMfaPendingSession(String email);

    String validateMfaPendingSession(String token);

    void revokeMfaPendingSession(String token);
}
