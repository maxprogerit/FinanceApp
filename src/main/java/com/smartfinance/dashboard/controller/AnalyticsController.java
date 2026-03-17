package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
