package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.service.EmailService;
import com.smartfinance.dashboard.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final UserService userService;
    private final EmailService emailService;

    // ── Page routes ───────────────────────────────────────────────────────────

    @GetMapping("/")
    public String index() { return "index"; }

    @GetMapping("/dashboard")
    public String dashboard() { return "dashboard"; }

    @GetMapping("/transactions")
    public String transactions() { return "transactions"; }

    @GetMapping("/investments")
    public String investments() { return "investments"; }

    @GetMapping("/analytics")
    public String analytics() { return "analytics"; }

    @GetMapping("/settings")
    public String settings() { return "settings"; }

    // ── Email verification ────────────────────────────────────────────────────

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("errorType", "invalid");
            return "verify-email";
        }
        try {
            userService.verifyEmail(token);
            model.addAttribute("success", true);
        } catch (RuntimeException e) {
            if ("EXPIRED_TOKEN".equals(e.getMessage())) {
                model.addAttribute("errorType", "expired");
            } else {
                model.addAttribute("errorType", "invalid");
            }
        }
        return "verify-email";
    }

    // ── Resend verification ────────────────────────────────────────────────────

    @GetMapping("/resend-verification")
    public String resendVerificationPage() {
        return "resend-verification";
    }

    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam String email, Model model) {
        try {
            Optional<User> optUser = userService.findByEmail(email);
            if (optUser.isPresent()) {
                User user = optUser.get();
                if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                    String token = userService.generateVerificationToken(user);
                    emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), token);
                }
                // Don't reveal if email is already verified or not found — always show success
            }
        } catch (Exception e) {
            // Silently absorb to prevent email enumeration
        }
        model.addAttribute("sent", true);
        return "resend-verification";
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        try {
            String token = userService.generatePasswordResetToken(email);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            emailService.sendPasswordResetEmail(email, user.getUsername(), token);
        } catch (Exception e) {
            // Always show the same response to prevent email enumeration
        }
        model.addAttribute("sent", true);
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("errorType", "invalid");
            return "reset-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            return "redirect:/reset-password?token=" + token;
        }
        if (password.length() < 12) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 12 characters.");
            return "redirect:/reset-password?token=" + token;
        }
        boolean success = userService.resetPassword(token, password);
        if (success) {
            model.addAttribute("success", true);
            return "reset-password";
        } else {
            model.addAttribute("errorType", "expired");
            return "reset-password";
        }
    }
}
