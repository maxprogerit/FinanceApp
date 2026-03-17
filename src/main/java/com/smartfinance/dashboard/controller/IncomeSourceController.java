package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.IncomeSource;
import com.smartfinance.dashboard.service.IncomeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/income-sources")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IncomeSourceController {

    private final IncomeSourceService incomeSourceService;

    @GetMapping
    public ResponseEntity<List<IncomeSource>> getAll() {
        return ResponseEntity.ok(incomeSourceService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody IncomeSource incomeSource) {
        try {
            return ResponseEntity.ok(incomeSourceService.create(incomeSource));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody IncomeSource incomeSource) {
        try {
            return ResponseEntity.ok(incomeSourceService.update(id, incomeSource));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            incomeSourceService.delete(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
