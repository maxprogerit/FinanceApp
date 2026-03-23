package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    /** GET /api/admin/users — list all users (safe fields only) */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @RequestParam(required = false) String search) {

        List<User> users = userRepository.findAll();

        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            users = users.stream()
                    .filter(u -> u.getEmail().toLowerCase().contains(q)
                            || u.getUsername().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> result = users.stream()
                .map(this::toAdminUserView)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /** GET /api/admin/user/{id} — get a single user's details */
    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(toAdminUserView(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/admin/user/{id} — delete a user account */
    @DeleteMapping("/user/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private Map<String, Object> toAdminUserView(User u) {
        return Map.of(
                "id",           u.getId(),
                "username",     u.getUsername(),
                "email",        u.getEmail(),
                "role",         u.getRole() != null ? u.getRole() : "USER",
                "plan",         u.getPlan().name(),
                "premiumActive",u.isPremiumActive(),
                "emailVerified",Boolean.TRUE.equals(u.getEmailVerified()),
                "provider",     u.getProvider() != null ? u.getProvider() : "local",
                "createdAt",    u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""
        );
    }
}
