package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.ForecastingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastingService forecastingService;
    private final SecurityUtils securityUtils;

    @GetMapping("/spending")
    public ResponseEntity<Map<String, Object>> forecastNextMonthSpending() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(forecastingService.forecastNextMonthSpending(user));
    }

    @GetMapping("/savings")
    public ResponseEntity<Map<String, Object>> forecastSavings(@RequestParam(defaultValue = "12") int months) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(forecastingService.forecastSavings(months, user));
    }
}
