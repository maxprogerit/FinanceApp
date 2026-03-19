package com.smartfinance.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends transactional emails: verification, password reset, subscription events.
 * All sends are async to avoid blocking the request thread.
 * Set app.mail.enabled=false in dev to skip actual sending.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String username, String token) {
        String link = baseUrl + "/verify-email?token=" + token;
        String subject = "Verify your Smart Finance Dashboard account";
        String html = """
                <h2>Welcome to Smart Finance Dashboard, %s!</h2>
                <p>Please verify your email address by clicking the link below:</p>
                <p><a href="%s" style="background:#4f46e5;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none;">Verify Email</a></p>
                <p>This link expires in 24 hours.</p>
                <p>If you did not create an account, you can ignore this email.</p>
                """.formatted(username, link);
        send(toEmail, subject, html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String username, String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        String subject = "Reset your Smart Finance Dashboard password";
        String html = """
                <h2>Password Reset Request</h2>
                <p>Hi %s,</p>
                <p>Click the button below to reset your password. This link expires in 1 hour.</p>
                <p><a href="%s" style="background:#dc2626;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none;">Reset Password</a></p>
                <p>If you did not request a password reset, please ignore this email.</p>
                """.formatted(username, link);
        send(toEmail, subject, html);
    }

    @Async
    public void sendSubscriptionConfirmation(String toEmail, String username, String planName, String renewalDate) {
        String subject = "Your Smart Finance Dashboard subscription is active";
        String html = """
                <h2>Subscription Confirmed!</h2>
                <p>Hi %s,</p>
                <p>Your <strong>%s</strong> subscription is now active.</p>
                <p>Next renewal: <strong>%s</strong></p>
                <p>You now have access to all premium features.</p>
                """.formatted(username, planName, renewalDate);
        send(toEmail, subject, html);
    }

    @Async
    public void sendSubscriptionCancelledEmail(String toEmail, String username) {
        String subject = "Your Smart Finance Dashboard subscription has been cancelled";
        String html = """
                <h2>Subscription Cancelled</h2>
                <p>Hi %s,</p>
                <p>Your subscription has been cancelled. You will retain access until the end of your current billing period.</p>
                <p>We're sorry to see you go. If this was a mistake, you can resubscribe at any time.</p>
                """.formatted(username);
        send(toEmail, subject, html);
    }

    private void send(String to, String subject, String htmlBody) {
        if (!mailEnabled) {
            log.info("Mail disabled — skipping send to {} (subject: {})", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to {} — {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
