package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.EmailService;
import com.smartfinance.dashboard.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

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
            model.addAttribute("error", "Invalid or missing verification token.");
            return "login";
        }
        try {
            userService.verifyEmail(token);
            model.addAttribute("message", "Email verified successfully! You can now log in.");
        } catch (Exception e) {
            model.addAttribute("error", "Verification link is invalid or has expired.");
        }
        return "login";
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email,
                                 RedirectAttributes redirectAttributes) {
        try {
            String token = userService.generatePasswordResetToken(email);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            emailService.sendPasswordResetEmail(email, user.getUsername(), token);
        } catch (Exception e) {
            // Always show success to prevent email enumeration
        }
        redirectAttributes.addFlashAttribute("message",
                "If that email exists, a reset link has been sent.");
        return "redirect:/login";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("error", "Invalid reset link.");
            return "login";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            return "redirect:/reset-password?token=" + token;
        }
        if (password.length() < 8) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 8 characters.");
            return "redirect:/reset-password?token=" + token;
        }
        boolean success = userService.resetPassword(token, password);
        if (success) {
            redirectAttributes.addFlashAttribute("message", "Password reset successfully! Please log in.");
            return "redirect:/login";
        } else {
            redirectAttributes.addFlashAttribute("error", "Reset link has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }
    }
}
