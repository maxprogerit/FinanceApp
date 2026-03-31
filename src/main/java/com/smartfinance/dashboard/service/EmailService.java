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
import java.math.BigDecimal;
import java.math.RoundingMode;

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

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String username, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
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
        String link = frontendUrl + "/reset-password?token=" + token;
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
                <p>You now have access to all Pro features.</p>
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

    /**
     * Weekly financial summary email.
     * Sent every Monday for the prior 7 days of activity.
     */
    @Async
    public void sendWeeklyReport(String toEmail, String username,
                                 BigDecimal income, BigDecimal expenses, BigDecimal net,
                                 double savingsRate, String topCategory,
                                 String periodStart, String periodEnd,
                                 String currency) {
        String subject = "Your weekly finance summary — " + periodEnd;
        String netColor  = net.compareTo(BigDecimal.ZERO) >= 0 ? "#16a34a" : "#dc2626";
        String netSign   = net.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        String rateStr   = String.format("%.1f", savingsRate);

        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px;background:#f9fafb;border-radius:12px;">
                    <h2 style="color:#4f46e5;margin-top:0;">Weekly Finance Summary</h2>
                    <p style="color:#6b7280;">Hi <strong>%s</strong> — here's how you did from %s to %s.</p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;">
                        <tr style="background:#fff;border-radius:8px;">
                            <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;">
                                <span style="color:#6b7280;font-size:0.85rem;">Total Income</span><br>
                                <strong style="font-size:1.25rem;color:#16a34a;">%s %,.2f</strong>
                            </td>
                        </tr>
                        <tr style="background:#fff;">
                            <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;">
                                <span style="color:#6b7280;font-size:0.85rem;">Total Expenses</span><br>
                                <strong style="font-size:1.25rem;color:#dc2626;">%s %,.2f</strong>
                            </td>
                        </tr>
                        <tr style="background:#fff;">
                            <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;">
                                <span style="color:#6b7280;font-size:0.85rem;">Net Flow</span><br>
                                <strong style="font-size:1.25rem;color:%s;">%s%s %,.2f</strong>
                            </td>
                        </tr>
                        <tr style="background:#fff;">
                            <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;">
                                <span style="color:#6b7280;font-size:0.85rem;">Savings Rate</span><br>
                                <strong style="font-size:1.25rem;">%s%%</strong>
                            </td>
                        </tr>
                        <tr style="background:#fff;">
                            <td style="padding:12px 16px;">
                                <span style="color:#6b7280;font-size:0.85rem;">Top Spending Category</span><br>
                                <strong style="font-size:1.1rem;">%s</strong>
                            </td>
                        </tr>
                    </table>
                    <a href="%s/analytics" style="display:inline-block;background:#4f46e5;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none;font-weight:600;">
                        View Full Analytics →
                    </a>
                    <p style="color:#9ca3af;font-size:0.75rem;margin-top:20px;">
                        You're receiving this because you have an account at Smart Finance Dashboard.
                    </p>
                </div>
                """.formatted(
                username, periodStart, periodEnd,
                currency, income,
                currency, expenses,
                netColor, netSign, currency, net,
                rateStr,
                topCategory,
                frontendUrl);

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
