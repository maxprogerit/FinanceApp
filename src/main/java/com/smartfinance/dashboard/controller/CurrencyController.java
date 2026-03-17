package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/currency")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CurrencyController {

    private final CurrencyService currencyService;

    /**
     * Returns exchange rates relative to USD.
     * Example: { "EUR": 0.9234, "GBP": 0.7891, "RSD": 107.80, ... }
     */
    @GetMapping("/rates")
    public ResponseEntity<Map<String, Double>> getRates() {
        return ResponseEntity.ok(currencyService.getRatesFromUSD());
    }
}
