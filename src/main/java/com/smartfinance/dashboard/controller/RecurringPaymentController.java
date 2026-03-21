package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.RecurringPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes detected recurring expense payments (Netflix, Spotify, etc.) derived from
 * the user's transaction history. Not related to Stripe billing subscriptions.
 */
@RestController
@RequestMapping("/api/recurring-payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecurringPaymentController {

    private final RecurringPaymentService recurringPaymentService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<List<RecurringPaymentService.RecurringPaymentSummary>> getAll() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(recurringPaymentService.detectRecurringPayments(user));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<RecurringPaymentService.RecurringPaymentSummary>> getUpcoming(
            @RequestParam(defaultValue = "7") int days) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(recurringPaymentService.getUpcomingPayments(days, user));
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
