package com.example.demo.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String resetLink);

    void sendVerificationEmail(String to, String verificationLink);

    void sendMfaCodeEmail(String to, String code);
}
