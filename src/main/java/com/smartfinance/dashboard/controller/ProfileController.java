package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.UserRepository;
import com.smartfinance.dashboard.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final SecurityUtils securityUtils;
    private final UserRepository userRepository;

    /** GET /api/profile — returns the current user's profile data */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(buildProfileResponse(user));
    }

    /** PUT /api/profile — updates editable profile fields */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody UpdateProfileRequest req) {
        User user = securityUtils.getCurrentUser();

        if (req.getBio() != null) {
            String bio = req.getBio().trim();
            if (bio.length() > 500) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Bio must be 500 characters or fewer"));
            }
            user.setBio(bio.isEmpty() ? null : bio);
        }

        if (req.getAvatarUrl() != null) {
            String url = req.getAvatarUrl().trim();
            user.setAvatarUrl(url.isEmpty() ? null : url);
        }

        userRepository.save(user);
        return ResponseEntity.ok(buildProfileResponse(user));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildProfileResponse(User user) {
        return Map.of(
                "username",     user.getUsername(),
                "email",        user.getEmail(),
                "plan",         user.getPlan().name(),
                "premiumActive",user.isPremiumActive(),
                "baseCurrency", user.getBaseCurrency() != null ? user.getBaseCurrency() : "USD",
                "theme",        user.getTheme() != null ? user.getTheme() : "light",
                "bio",          user.getBio() != null ? user.getBio() : "",
                "avatarUrl",    user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "createdAt",    user.getCreatedAt() != null ? user.getCreatedAt().toString() : "",
                "role",         user.getRole() != null ? user.getRole() : "USER"
        );
    }

    // ── request DTO ───────────────────────────────────────────────────────────

    @lombok.Data
    public static class UpdateProfileRequest {
        private String bio;
        private String avatarUrl;
    }
}
