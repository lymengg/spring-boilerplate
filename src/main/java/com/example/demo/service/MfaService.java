package com.example.demo.service;

public interface MfaService {

    String generateTotpSecret();

    String generateQrUri(String username, String secret);

    String generateOtpAuthUri(String username, String secret);

    boolean verifyTotpCode(String secret, String code);

    String generateEmailOtp();

    void storeEmailOtp(String username, String code);

    boolean verifyEmailOtp(String username, String code);

    String storeMfaPendingSession(String username);

    String validateMfaPendingSession(String token);

    void revokeMfaPendingSession(String token);
}
