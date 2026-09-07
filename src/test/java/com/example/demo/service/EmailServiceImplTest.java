package com.example.demo.service;

import com.example.demo.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl mailEnabledService;
    private EmailServiceImpl mailDisabledService;

    @BeforeEach
    void setUp() {
        mailEnabledService = new EmailServiceImpl(true, mailSender);
        mailDisabledService = new EmailServiceImpl(false, null);
    }

    @Test
    @DisplayName("Password reset email is skipped when mail is disabled")
    void passwordResetEmailSkippedWhenMailDisabled() {
        mailDisabledService.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset?token=abc");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Verification email is skipped when mail is disabled")
    void verificationEmailSkippedWhenMailDisabled() {
        mailDisabledService.sendVerificationEmail("user@example.com", "http://localhost:8080/verify?token=abc");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("MFA code email is skipped when mail is disabled")
    void mfaCodeEmailSkippedWhenMailDisabled() {
        mailDisabledService.sendMfaCodeEmail("user@example.com", "123456");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Password reset email sets recipient, subject and reset link and is sent")
    void passwordResetEmailSetsToSubjectAndBody() {
        mailEnabledService.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset?token=abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("Password Reset Request");
        assertThat(message.getText()).contains("http://localhost:8080/reset?token=abc");
    }

    @Test
    @DisplayName("Verification email sets recipient, subject and verification link and is sent")
    void verificationEmailSetsToSubjectAndBody() {
        mailEnabledService.sendVerificationEmail("user@example.com", "http://localhost:8080/verify?token=abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("Email Verification");
        assertThat(message.getText()).contains("http://localhost:8080/verify?token=abc");
    }

    @Test
    @DisplayName("MFA code email sets recipient, subject, code and expiry note and is sent")
    void mfaCodeEmailSetsToSubjectAndBody() {
        mailEnabledService.sendMfaCodeEmail("user@example.com", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("Your MFA Verification Code");
        assertThat(message.getText()).contains("123456");
        assertThat(message.getText()).contains("5 minutes");
    }

    @Test
    @DisplayName("Password reset email send failure is swallowed so the auth flow is never broken")
    void passwordResetEmailSendFailureSwallowed() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailEnabledService.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset?token=abc"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Verification email send failure is swallowed so the auth flow is never broken")
    void verificationEmailSendFailureSwallowed() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailEnabledService.sendVerificationEmail("user@example.com", "http://localhost:8080/verify?token=abc"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MFA code email send failure is swallowed so the auth flow is never broken")
    void mfaCodeEmailSendFailureSwallowed() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailEnabledService.sendMfaCodeEmail("user@example.com", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mail enabled with a null mailSender swallows the NPE from send")
    void mailEnabledWithNullMailSenderSwallowsNpe() {
        EmailServiceImpl nullSenderService = new EmailServiceImpl(true, null);

        assertThatCode(() -> nullSenderService.sendPasswordResetEmail("user@example.com", "http://localhost:8080/reset?token=abc"))
                .doesNotThrowAnyException();
        assertThatCode(() -> nullSenderService.sendVerificationEmail("user@example.com", "http://localhost:8080/verify?token=abc"))
                .doesNotThrowAnyException();
        assertThatCode(() -> nullSenderService.sendMfaCodeEmail("user@example.com", "123456"))
                .doesNotThrowAnyException();
    }
}
