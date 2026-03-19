package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<List<SubscriptionService.SubscriptionSummary>> getAll() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(subscriptionService.detectSubscriptions(user));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<SubscriptionService.SubscriptionSummary>> getUpcoming(
            @RequestParam(defaultValue = "7") int days) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(subscriptionService.getUpcomingSubscriptions(days, user));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("not found")) {
            return ResponseEntity.notFound().build();
        }
        if (msg.contains("unauthorized") || msg.contains("forbidden") || msg.contains("access denied")) {
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        }
        return ResponseEntity.status(500).body(Map.of("error", ex.getMessage()));
    }
}
