package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.JwtService;
import com.smartfinance.dashboard.service.EmailService;
import com.smartfinance.dashboard.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            Model model) {
        if (error != null) {
            String message = switch (error) {
                case "unverified" -> "Please verify your email before logging in. Check your inbox for the verification link.";
                case "oauth" -> "Google sign-in failed. Please try again or use email and password.";
                default -> "Invalid email or password. Please try again.";
            };
            model.addAttribute("error", message);
            if ("unverified".equals(error)) {
                model.addAttribute("resendVerification", true);
            }
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes) {
        try {
            User user = userService.register(username, email, password);
            // Generate token with 24-hour expiry and send verification email
            String token = userService.generateVerificationToken(user);
            emailService.sendVerificationEmail(email, username, token);
            redirectAttributes.addFlashAttribute("message",
                    "Account created! Please check your email to verify your address, then log in.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    @PostMapping("/api/auth/token")
    @ResponseBody
    public ResponseEntity<?> getToken(@RequestParam String username, @RequestParam String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            UserDetails userDetails = userService.loadUserByUsername(username);
            String token = jwtService.generateToken(userDetails);
            return ResponseEntity.ok(Map.of("token", token, "username", username));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }
}
