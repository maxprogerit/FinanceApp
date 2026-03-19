package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.CategorizationRule;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.CategorizationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categorization-rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CategorizationRuleController {

    private final CategorizationRuleService ruleService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<List<CategorizationRule>> getAll() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(ruleService.findAll(user));
    }

    @PostMapping
    public ResponseEntity<CategorizationRule> create(@RequestBody CategorizationRule rule) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(ruleService.create(rule, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorizationRule> update(@PathVariable Long id, @RequestBody CategorizationRule rule) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(ruleService.update(id, rule, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        ruleService.delete(id, user);
        return ResponseEntity.ok().build();
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
