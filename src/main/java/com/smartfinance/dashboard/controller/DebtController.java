package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Debt;
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

    @GetMapping
    public ResponseEntity<List<Debt>> getAll() {
        return ResponseEntity.ok(debtService.getAllDebts());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(debtService.getSummary());
    }

    @PostMapping
    public ResponseEntity<Debt> create(@RequestBody Debt debt) {
        return ResponseEntity.ok(debtService.createDebt(debt));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Debt> update(@PathVariable Long id, @RequestBody Debt debt) {
        return ResponseEntity.ok(debtService.updateDebt(id, debt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        debtService.deleteDebt(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/settle")
    public ResponseEntity<Debt> settle(@PathVariable Long id) {
        return ResponseEntity.ok(debtService.settleDebt(id));
    }
}
