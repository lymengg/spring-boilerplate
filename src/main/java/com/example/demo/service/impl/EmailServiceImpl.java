package com.example.demo.service.impl;

import com.example.demo.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    private final boolean mailEnabled;

    @Autowired
    public EmailServiceImpl(
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @org.springframework.lang.Nullable JavaMailSender mailSender) {
        this.mailEnabled = mailEnabled;
        this.mailSender = mailSender;
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        if (!mailEnabled) {
            log.info("Mail disabled — skipping password reset email to {}", to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Password Reset Request");
            message.setText("To reset your password, click the link below:\n\n" + resetLink);
            mailSender.send(message);
            log.info("Password reset email sent to: {}", to);
        } catch (Exception e) {
            log.warn("Failed to send password reset email to {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        if (!mailEnabled) {
            log.info("Mail disabled — skipping verification email to {}", to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Email Verification");
            message.setText("To verify your email, click the link below:\n\n" + verificationLink);
            mailSender.send(message);
            log.info("Verification email sent to: {}", to);
        } catch (Exception e) {
            log.warn("Failed to send verification email to {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void sendMfaCodeEmail(String to, String code) {
        if (!mailEnabled) {
            log.info("Mail disabled — skipping MFA code email to {}", to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Your MFA Verification Code");
            message.setText("Your multi-factor authentication code is: " + code + "\n\nThis code will expire in 5 minutes.\nIf you did not request this code, please ignore this email.");
            mailSender.send(message);
            log.info("MFA code email sent to: {}", to);
        } catch (Exception e) {
            log.warn("Failed to send MFA code email to {}: {}", to, e.getMessage());
        }
    }
}
