package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.FinancialGoal;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.FinancialGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GoalController {

    private final FinancialGoalService goalService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<FinancialGoal> createGoal(@RequestBody FinancialGoal goal) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(goalService.createGoal(goal, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FinancialGoal> updateGoal(@PathVariable Long id, @RequestBody FinancialGoal goal) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(goalService.updateGoal(id, goal, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        goalService.deleteGoal(id, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<FinancialGoal> getGoal(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(goalService.getGoalById(id, user));
    }

    @GetMapping
    public ResponseEntity<List<FinancialGoal>> getAllGoals() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(goalService.getAllGoals(user));
    }

    @GetMapping("/active")
    public ResponseEntity<List<FinancialGoal>> getActiveGoals() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(goalService.getActiveGoals(user));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<FinancialGoal>> getOverdueGoals() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(goalService.getOverdueGoals(user));
    }

    @PatchMapping("/{id}/add")
    public ResponseEntity<FinancialGoal> addToGoal(@PathVariable Long id, @RequestParam BigDecimal amount) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(goalService.addToGoal(id, amount, user));
    }

    @PatchMapping("/{id}/withdraw")
    public ResponseEntity<FinancialGoal> withdrawFromGoal(@PathVariable Long id, @RequestParam BigDecimal amount) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(goalService.withdrawFromGoal(id, amount, user));
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
