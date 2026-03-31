package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.dto.InvestmentDTO;
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
    private final SecurityUtils     securityUtils;

    @PostMapping
    public ResponseEntity<InvestmentDTO> createInvestment(@RequestBody Investment investment) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(InvestmentDTO.from(investmentService.createInvestment(investment, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InvestmentDTO> updateInvestment(@PathVariable Long id,
                                                          @RequestBody Investment investment) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(InvestmentDTO.from(investmentService.updateInvestment(id, investment, user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestment(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        investmentService.deleteInvestment(id, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestmentDTO> getInvestment(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(InvestmentDTO.from(investmentService.getInvestmentById(id, user)));
    }

    @GetMapping
    public ResponseEntity<List<InvestmentDTO>> getAllInvestments() {
        User user = securityUtils.getCurrentUser();
        List<InvestmentDTO> dtos = investmentService.getAllInvestments(user)
                .stream().map(InvestmentDTO::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/asset-type/{assetType}")
    public ResponseEntity<List<InvestmentDTO>> getInvestmentsByAssetType(@PathVariable String assetType) {
        User user = securityUtils.getCurrentUser();
        List<InvestmentDTO> dtos = investmentService.getInvestmentsByAssetType(assetType, user)
                .stream().map(InvestmentDTO::from).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/portfolio/summary")
    public ResponseEntity<Map<String, Object>> getPortfolioSummary() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(Map.of(
                "totalPortfolioValue",  investmentService.getTotalPortfolioValue(user),
                "totalInvestmentValue", investmentService.getTotalInvestmentValue(user),
                "totalProfitLoss",      investmentService.getTotalProfitLoss(user),
                "profitLossPercentage", investmentService.getTotalProfitLossPercentage(user)
        ));
    }

    @GetMapping("/portfolio/by-asset-type")
    public ResponseEntity<Map<String, BigDecimal>> getPortfolioByAssetType() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(investmentService.getPortfolioByAssetType(user));
    }

    @PatchMapping("/{id}/price")
    public ResponseEntity<Void> updateInvestmentPrice(@PathVariable Long id,
                                                      @RequestParam BigDecimal price) {
        User user = securityUtils.getCurrentUser();
        investmentService.updateInvestmentPrice(id, price, user);
        return ResponseEntity.ok().build();
    }

    /**
     * Triggers a live market-price refresh for all of the current user's investments.
     * Returns {"updated": N} where N is the number of prices actually changed.
     * Returns {"updated": 0} when market.data.enabled=false.
     */
    @GetMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshPrices() {
        User user = securityUtils.getCurrentUser();
        int updated = investmentService.refreshPrices(user);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("not found"))
            return ResponseEntity.notFound().build();
        if (msg.contains("unauthorized") || msg.contains("forbidden") || msg.contains("access denied"))
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        return ResponseEntity.status(500).body(Map.of("error", ex.getMessage()));
    }
}
