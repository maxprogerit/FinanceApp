package com.smartfinance.dashboard.security;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility bean for resolving the currently authenticated User entity.
 * Injected into API controllers to enforce per-user data isolation.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserService userService;

    /**
     * Returns the User entity for the currently authenticated principal.
     * Works for both form-login (principal = username) and OAuth2 (principal = email).
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("No authenticated user");
        }
        String principal = auth.getName();
        // Form-login: principal is the username. OAuth2 (Google): principal is the email.
        return userService.findByUsername(principal)
                .or(() -> userService.findByEmail(principal))
                .orElseThrow(() -> new RuntimeException("User not found: " + principal));
    }

    /** Returns the username of the currently authenticated principal. */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("No authenticated user");
        }
        return auth.getName();
    }
}
