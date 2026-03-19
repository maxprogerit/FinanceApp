package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionService transactionService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.createTransaction(transaction, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(@PathVariable Long id, @RequestBody Transaction transaction) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.updateTransaction(id, transaction, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        transactionService.deleteTransaction(id, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.getTransactionById(id, user));
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.getAllTransactions(user));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<Transaction>> getTransactionsByType(@PathVariable String type) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.getTransactionsByType(type, user));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<Transaction>> getTransactionsByCategory(@PathVariable String category) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.getTransactionsByCategory(category, user));
    }

    @GetMapping("/range")
    public ResponseEntity<List<Transaction>> getTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.getTransactionsByDateRange(startDate, endDate, user));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, BigDecimal>> getTransactionSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        User user = securityUtils.getCurrentUser();
        BigDecimal totalIncome = transactionService.getTotalIncomeForPeriod(startDate, endDate, user);
        BigDecimal totalExpenses = transactionService.getTotalExpensesForPeriod(startDate, endDate, user);
        return ResponseEntity.ok(Map.of(
                "totalIncome", totalIncome,
                "totalExpenses", totalExpenses,
                "netSavings", totalIncome.subtract(totalExpenses)
        ));
    }

    @GetMapping("/expenses-by-category")
    public ResponseEntity<Map<String, BigDecimal>> getExpensesByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.getExpensesByCategory(startDate, endDate, user));
    }

    @GetMapping("/tags")
    public ResponseEntity<Set<String>> getAllTags() {
        User user = securityUtils.getCurrentUser();
        Set<String> tags = new TreeSet<>();
        transactionService.getAllTransactions(user).forEach(t -> {
            if (t.getTags() != null && !t.getTags().isBlank()) {
                for (String tag : t.getTags().split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) tags.add(trimmed);
                }
            }
        });
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/by-tag")
    public ResponseEntity<List<Transaction>> getByTag(@RequestParam String tag) {
        User user = securityUtils.getCurrentUser();
        String lower = tag.toLowerCase().trim();
        List<Transaction> filtered = transactionService.getAllTransactions(user).stream()
                .filter(t -> t.getTags() != null && t.getTags().toLowerCase().contains(lower))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
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
