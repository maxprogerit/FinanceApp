package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransactionController {
    
    private final TransactionService transactionService;
    
    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction) {
        return ResponseEntity.ok(transactionService.createTransaction(transaction));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(@PathVariable Long id, @RequestBody Transaction transaction) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, transaction));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }
    
    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }
    
    @GetMapping("/type/{type}")
    public ResponseEntity<List<Transaction>> getTransactionsByType(@PathVariable String type) {
        return ResponseEntity.ok(transactionService.getTransactionsByType(type));
    }
    
    @GetMapping("/category/{category}")
    public ResponseEntity<List<Transaction>> getTransactionsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(transactionService.getTransactionsByCategory(category));
    }
    
    @GetMapping("/range")
    public ResponseEntity<List<Transaction>> getTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(transactionService.getTransactionsByDateRange(startDate, endDate));
    }
    
    @GetMapping("/summary")
    public ResponseEntity<Map<String, BigDecimal>> getTransactionSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        BigDecimal totalIncome = transactionService.getTotalIncomeForPeriod(startDate, endDate);
        BigDecimal totalExpenses = transactionService.getTotalExpensesForPeriod(startDate, endDate);
        
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
        return ResponseEntity.ok(transactionService.getExpensesByCategory(startDate, endDate));
    }

    @GetMapping("/tags")
    public ResponseEntity<java.util.Set<String>> getAllTags() {
        java.util.Set<String> tags = new java.util.TreeSet<>();
        transactionService.getAllTransactions().forEach(t -> {
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
        String lower = tag.toLowerCase().trim();
        List<Transaction> filtered = transactionService.getAllTransactions().stream()
                .filter(t -> t.getTags() != null && t.getTags().toLowerCase().contains(lower))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(filtered);
    }
}
