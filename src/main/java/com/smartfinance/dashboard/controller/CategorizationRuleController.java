package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.CategorizationRule;
import com.smartfinance.dashboard.service.CategorizationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorization-rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CategorizationRuleController {

    private final CategorizationRuleService ruleService;

    @GetMapping
    public ResponseEntity<List<CategorizationRule>> getAll() {
        return ResponseEntity.ok(ruleService.findAll());
    }

    @PostMapping
    public ResponseEntity<CategorizationRule> create(@RequestBody CategorizationRule rule) {
        return ResponseEntity.ok(ruleService.create(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorizationRule> update(@PathVariable Long id, @RequestBody CategorizationRule rule) {
        return ResponseEntity.ok(ruleService.update(id, rule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return ResponseEntity.ok().build();
    }
}
