package com.smartfinance.dashboard.controller;

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
    
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardAnalytics() {
        return ResponseEntity.ok(analyticsService.getDashboardAnalytics());
    }
    
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getMonthlyTrends(@RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(analyticsService.getMonthlyTrends(months));
    }
    
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategoryAnalysis() {
        return ResponseEntity.ok(analyticsService.getCategoryAnalysis());
    }
    
    @GetMapping("/insights")
    public ResponseEntity<List<String>> getFinancialInsights() {
        return ResponseEntity.ok(analyticsService.generateFinancialInsights());
    }

    @GetMapping("/storage-distribution")
    public ResponseEntity<Map<String, Double>> getStorageDistribution() {
        return ResponseEntity.ok(analyticsService.getStorageDistribution());
    }

    @GetMapping("/income-sources")
    public ResponseEntity<Map<String, Double>> getIncomeSources(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int year) {
        LocalDateTime now = LocalDateTime.now();
        int m = month > 0 ? month : now.getMonthValue();
        int y = year  > 0 ? year  : now.getYear();
        return ResponseEntity.ok(analyticsService.getIncomeBySource(m, y));
    }

    @GetMapping("/health-score")
    public ResponseEntity<Map<String, Object>> getHealthScore() {
        return ResponseEntity.ok(analyticsService.getHealthScore());
    }
}
