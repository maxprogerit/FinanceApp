package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.FinancialGoal;
import com.smartfinance.dashboard.service.FinancialGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GoalController {
    
    private final FinancialGoalService goalService;
    
    @PostMapping
    public ResponseEntity<FinancialGoal> createGoal(@RequestBody FinancialGoal goal) {
        return ResponseEntity.ok(goalService.createGoal(goal));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<FinancialGoal> updateGoal(@PathVariable Long id, @RequestBody FinancialGoal goal) {
        return ResponseEntity.ok(goalService.updateGoal(id, goal));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable Long id) {
        goalService.deleteGoal(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<FinancialGoal> getGoal(@PathVariable Long id) {
        return ResponseEntity.ok(goalService.getGoalById(id));
    }
    
    @GetMapping
    public ResponseEntity<List<FinancialGoal>> getAllGoals() {
        return ResponseEntity.ok(goalService.getAllGoals());
    }
    
    @GetMapping("/active")
    public ResponseEntity<List<FinancialGoal>> getActiveGoals() {
        return ResponseEntity.ok(goalService.getActiveGoals());
    }
    
    @GetMapping("/overdue")
    public ResponseEntity<List<FinancialGoal>> getOverdueGoals() {
        return ResponseEntity.ok(goalService.getOverdueGoals());
    }
    
    @PatchMapping("/{id}/add")
    public ResponseEntity<FinancialGoal> addToGoal(@PathVariable Long id, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(goalService.addToGoal(id, amount));
    }
    
    @PatchMapping("/{id}/withdraw")
    public ResponseEntity<FinancialGoal> withdrawFromGoal(@PathVariable Long id, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(goalService.withdrawFromGoal(id, amount));
    }
}
