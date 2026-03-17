package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.service.ForecastingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ForecastController {
    
    private final ForecastingService forecastingService;
    
    @GetMapping("/spending")
    public ResponseEntity<Map<String, Object>> forecastNextMonthSpending() {
        return ResponseEntity.ok(forecastingService.forecastNextMonthSpending());
    }
    
    @GetMapping("/savings")
    public ResponseEntity<Map<String, Object>> forecastSavings(@RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(forecastingService.forecastSavings(months));
    }
}
