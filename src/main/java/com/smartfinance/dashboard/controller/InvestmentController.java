package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Investment;
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
    
    @PostMapping
    public ResponseEntity<Investment> createInvestment(@RequestBody Investment investment) {
        return ResponseEntity.ok(investmentService.createInvestment(investment));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Investment> updateInvestment(@PathVariable Long id, @RequestBody Investment investment) {
        return ResponseEntity.ok(investmentService.updateInvestment(id, investment));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestment(@PathVariable Long id) {
        investmentService.deleteInvestment(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Investment> getInvestment(@PathVariable Long id) {
        return ResponseEntity.ok(investmentService.getInvestmentById(id));
    }
    
    @GetMapping
    public ResponseEntity<List<Investment>> getAllInvestments() {
        return ResponseEntity.ok(investmentService.getAllInvestments());
    }
    
    @GetMapping("/asset-type/{assetType}")
    public ResponseEntity<List<Investment>> getInvestmentsByAssetType(@PathVariable String assetType) {
        return ResponseEntity.ok(investmentService.getInvestmentsByAssetType(assetType));
    }
    
    @GetMapping("/portfolio/summary")
    public ResponseEntity<Map<String, Object>> getPortfolioSummary() {
        return ResponseEntity.ok(Map.of(
                "totalPortfolioValue", investmentService.getTotalPortfolioValue(),
                "totalInvestmentValue", investmentService.getTotalInvestmentValue(),
                "totalProfitLoss", investmentService.getTotalProfitLoss(),
                "profitLossPercentage", investmentService.getTotalProfitLossPercentage()
        ));
    }
    
    @GetMapping("/portfolio/by-asset-type")
    public ResponseEntity<Map<String, BigDecimal>> getPortfolioByAssetType() {
        return ResponseEntity.ok(investmentService.getPortfolioByAssetType());
    }
    
    @PatchMapping("/{id}/price")
    public ResponseEntity<Void> updateInvestmentPrice(@PathVariable Long id, @RequestParam BigDecimal price) {
        investmentService.updateInvestmentPrice(id, price);
        return ResponseEntity.ok().build();
    }
}
