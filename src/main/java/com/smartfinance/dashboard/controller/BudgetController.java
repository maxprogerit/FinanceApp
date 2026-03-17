package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Budget;
import com.smartfinance.dashboard.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BudgetController {
    
    private final BudgetService budgetService;
    
    @PostMapping
    public ResponseEntity<Budget> createBudget(@RequestBody Budget budget) {
        return ResponseEntity.ok(budgetService.createBudget(budget));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Budget> updateBudget(@PathVariable Long id, @RequestBody Budget budget) {
        return ResponseEntity.ok(budgetService.updateBudget(id, budget));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long id) {
        budgetService.deleteBudget(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Budget> getBudget(@PathVariable Long id) {
        return ResponseEntity.ok(budgetService.getBudgetById(id));
    }
    
    @GetMapping
    public ResponseEntity<List<Budget>> getAllBudgets() {
        return ResponseEntity.ok(budgetService.getAllBudgets());
    }
    
    @GetMapping("/active")
    public ResponseEntity<List<Budget>> getActiveBudgets() {
        return ResponseEntity.ok(budgetService.getActiveBudgets());
    }
    
    @GetMapping("/exceeding/{threshold}")
    public ResponseEntity<List<Budget>> getBudgetsExceedingThreshold(@PathVariable double threshold) {
        return ResponseEntity.ok(budgetService.getBudgetsExceedingThreshold(threshold));
    }
}
