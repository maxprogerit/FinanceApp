package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides basic current-user information to the frontend.
 * Used on page load to initialise plan state and user preferences.
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final SecurityUtils securityUtils;
    private final UserService   userService;

    /**
     * Returns core user info including plan tier.
     * Frontend uses this to gate premium features.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        User user = securityUtils.getCurrentUser();
        Map<String, Object> resp = new HashMap<>();
        resp.put("username",      user.getUsername());
        resp.put("email",         user.getEmail());
        resp.put("plan",          user.getPlan() != null ? user.getPlan().name() : "FREE");
        resp.put("premiumActive", user.isPremiumActive());
        resp.put("baseCurrency",  user.getBaseCurrency());
        resp.put("theme",         user.getTheme());
        resp.put("role",          user.getRole() != null ? user.getRole() : "USER");
        return ResponseEntity.ok(resp);
    }

    /**
     * PUT /api/user/currency
     * Persists the user's chosen base currency to the database.
     *
     * Request body: {"currency": "RSD"}
     */
    @PutMapping("/currency")
    public ResponseEntity<?> updateCurrency(@RequestBody Map<String, String> body) {
        String currency = body.get("currency");
        if (currency == null || currency.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "currency is required"));
        }
        try {
            User user = securityUtils.getCurrentUser();
            userService.updateBaseCurrency(user, currency);
            return ResponseEntity.ok(Map.of("baseCurrency", user.getBaseCurrency()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
