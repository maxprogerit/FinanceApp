package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Alert;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlertController {

    private final AlertService alertService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<List<Alert>> getAllAlerts() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(alertService.getAllAlerts(user));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<Alert>> getUnreadAlerts() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(alertService.getUnreadAlerts(user));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Alert>> getRecentAlerts() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(alertService.getRecentAlerts(user));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(alertService.getUnreadCount(user));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Alert> markAsRead(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(alertService.markAsRead(id, user));
    }

    @PatchMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsRead() {
        User user = securityUtils.getCurrentUser();
        alertService.markAllAsRead(user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        alertService.deleteAlert(id, user);
        return ResponseEntity.ok().build();
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
