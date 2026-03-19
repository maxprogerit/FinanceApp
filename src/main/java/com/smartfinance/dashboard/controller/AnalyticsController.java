package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SecurityUtils securityUtils;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardAnalytics() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(analyticsService.getDashboardAnalytics(user));
    }

    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getMonthlyTrends(@RequestParam(defaultValue = "6") int months) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(analyticsService.getMonthlyTrends(months, user));
    }

    @GetMapping("/trends/daily")
    public ResponseEntity<Map<String, Object>> getDailyTrends(@RequestParam(defaultValue = "30") int days) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(analyticsService.getDailyTrends(days, user));
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategoryAnalysis() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(analyticsService.getCategoryAnalysis(user));
    }

    @GetMapping("/insights")
    public ResponseEntity<List<String>> getFinancialInsights() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(analyticsService.generateFinancialInsights(user));
    }

    @GetMapping("/storage-distribution")
    public ResponseEntity<Map<String, Double>> getStorageDistribution() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(analyticsService.getStorageDistribution(user));
    }

    @GetMapping("/income-sources")
    public ResponseEntity<Map<String, Double>> getIncomeSources(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int year) {
        User user = securityUtils.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        int m = month > 0 ? month : now.getMonthValue();
        int y = year  > 0 ? year  : now.getYear();
        return ResponseEntity.ok(analyticsService.getIncomeBySource(m, y, user));
    }

    @GetMapping("/health-score")
    public ResponseEntity<Map<String, Object>> getHealthScore() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(analyticsService.getHealthScore(user));
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
