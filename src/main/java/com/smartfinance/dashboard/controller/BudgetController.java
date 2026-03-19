package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Budget;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BudgetController {

    private final BudgetService budgetService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<Budget> createBudget(@RequestBody Budget budget) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(budgetService.createBudget(budget, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Budget> updateBudget(@PathVariable Long id, @RequestBody Budget budget) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(budgetService.updateBudget(id, budget, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        budgetService.deleteBudget(id, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Budget> getBudget(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(budgetService.getBudgetById(id, user));
    }

    @GetMapping
    public ResponseEntity<List<Budget>> getAllBudgets() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(budgetService.getAllBudgets(user));
    }

    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate() {
        User user = securityUtils.getCurrentUser();
        int count = budgetService.recalculateAllBudgetSpending(user);
        return ResponseEntity.ok(Map.of("recalculated", count, "message", count + " budget(s) recalculated."));
    }

    @GetMapping("/active")
    public ResponseEntity<List<Budget>> getActiveBudgets() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(budgetService.getActiveBudgets(user));
    }

    @GetMapping("/exceeding/{threshold}")
    public ResponseEntity<List<Budget>> getBudgetsExceedingThreshold(@PathVariable double threshold) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(budgetService.getBudgetsExceedingThreshold(threshold, user));
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
