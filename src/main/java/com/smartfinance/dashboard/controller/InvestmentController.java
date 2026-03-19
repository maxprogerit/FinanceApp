package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Investment;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.InvestmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InvestmentController {

    private final InvestmentService investmentService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<Investment> createInvestment(@RequestBody Investment investment) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(investmentService.createInvestment(investment, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Investment> updateInvestment(@PathVariable Long id, @RequestBody Investment investment) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(investmentService.updateInvestment(id, investment, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestment(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        investmentService.deleteInvestment(id, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Investment> getInvestment(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(investmentService.getInvestmentById(id, user));
    }

    @GetMapping
    public ResponseEntity<List<Investment>> getAllInvestments() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(investmentService.getAllInvestments(user));
    }

    @GetMapping("/asset-type/{assetType}")
    public ResponseEntity<List<Investment>> getInvestmentsByAssetType(@PathVariable String assetType) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(investmentService.getInvestmentsByAssetType(assetType, user));
    }

    @GetMapping("/portfolio/summary")
    public ResponseEntity<Map<String, Object>> getPortfolioSummary() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(Map.of(
                "totalPortfolioValue", investmentService.getTotalPortfolioValue(user),
                "totalInvestmentValue", investmentService.getTotalInvestmentValue(user),
                "totalProfitLoss", investmentService.getTotalProfitLoss(user),
                "profitLossPercentage", investmentService.getTotalProfitLossPercentage(user)
        ));
    }

    @GetMapping("/portfolio/by-asset-type")
    public ResponseEntity<Map<String, BigDecimal>> getPortfolioByAssetType() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(investmentService.getPortfolioByAssetType(user));
    }

    @PatchMapping("/{id}/price")
    public ResponseEntity<Void> updateInvestmentPrice(@PathVariable Long id, @RequestParam BigDecimal price) {
        User user = securityUtils.getCurrentUser();
        investmentService.updateInvestmentPrice(id, price, user);
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
