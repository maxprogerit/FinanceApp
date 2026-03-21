package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides basic current-user information to the frontend.
 * Used on page load to initialise plan state and user preferences.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final SecurityUtils securityUtils;

    /**
     * Returns core user info including plan tier.
     * Frontend uses this to gate premium features.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        User user = securityUtils.getCurrentUser();
        Map<String, Object> resp = new HashMap<>();
        resp.put("username",     user.getUsername());
        resp.put("email",        user.getEmail());
        resp.put("plan",         user.getPlan() != null ? user.getPlan().name() : "FREE");
        resp.put("premiumActive", user.isPremiumActive());
        resp.put("baseCurrency", user.getBaseCurrency());
        resp.put("theme",        user.getTheme());
        return ResponseEntity.ok(resp);
    }
}
