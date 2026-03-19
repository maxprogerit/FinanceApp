package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Debt;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.DebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DebtController {

    private final DebtService debtService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<List<Debt>> getAll() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(debtService.getAllDebts(user));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(debtService.getSummary(user));
    }

    @PostMapping
    public ResponseEntity<Debt> create(@RequestBody Debt debt) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(debtService.createDebt(debt, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Debt> update(@PathVariable Long id, @RequestBody Debt debt) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(debtService.updateDebt(id, debt, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        debtService.deleteDebt(id, user);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/settle")
    public ResponseEntity<Debt> settle(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(debtService.settleDebt(id, user));
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
