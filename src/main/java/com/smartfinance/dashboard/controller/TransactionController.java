package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.dto.ParsedTransactionDTO;
import com.smartfinance.dashboard.dto.QuickAddDTO;
import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.OcrService;
import com.smartfinance.dashboard.service.PlanAccessService;
import com.smartfinance.dashboard.service.TextParserService;
import com.smartfinance.dashboard.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    private final TransactionService   transactionService;
    private final TextParserService    textParserService;
    private final OcrService           ocrService;
    private final PlanAccessService    planAccessService;
    private final SecurityUtils        securityUtils;

    // ── Standard CRUD ─────────────────────────────────────────────────────────

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
        BigDecimal totalIncome   = transactionService.getTotalIncomeForPeriod(startDate, endDate, user);
        BigDecimal totalExpenses = transactionService.getTotalExpensesForPeriod(startDate, endDate, user);
        return ResponseEntity.ok(Map.of(
                "totalIncome",   totalIncome,
                "totalExpenses", totalExpenses,
                "netSavings",    totalIncome.subtract(totalExpenses)
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
        User user  = securityUtils.getCurrentUser();
        String lower = tag.toLowerCase().trim();
        List<Transaction> filtered = transactionService.getAllTransactions(user).stream()
                .filter(t -> t.getTags() != null && t.getTags().toLowerCase().contains(lower))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    // ── Quick Add (all plans) ─────────────────────────────────────────────────

    /**
     * POST /api/transactions/quick
     * Minimal payload: {amount, type, category?, description?}
     * Server fills in: user, transactionDate=now, currency=baseCurrency.
     * Category is auto-detected from description when omitted.
     */
    @PostMapping("/quick")
    public ResponseEntity<Transaction> quickAdd(@RequestBody QuickAddDTO dto) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(transactionService.quickAdd(dto, user));
    }

    // ── Text parser (all plans) ───────────────────────────────────────────────

    /**
     * POST /api/transactions/parse
     * Parses a natural-language string like "500 coffee" or "+30000 salary".
     * Does NOT save a transaction — returns the parsed data for the frontend to review.
     */
    @PostMapping("/parse")
    public ResponseEntity<?> parseText(@RequestBody Map<String, String> body) {
        User   user  = securityUtils.getCurrentUser();
        String input = body.get("text");
        if (input == null || input.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        String currency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "USD";
        ParsedTransactionDTO parsed = textParserService.parseSimpleInput(input, currency);
        if (parsed == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not parse input"));
        }
        return ResponseEntity.ok(parsed);
    }

    // ── Receipt OCR (PRO+) ────────────────────────────────────────────────────

    /**
     * POST /api/transactions/scan-receipt
     * Accepts a receipt image (multipart), runs OCR, returns extracted transaction fields.
     * Does NOT save a transaction — returns the parsed data for the frontend to review.
     * Requires PRO plan.
     */
    @PostMapping(value = "/scan-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> scanReceipt(@RequestParam("image") MultipartFile image) {
        User user = securityUtils.getCurrentUser();
        if (!planAccessService.hasAccess(user, "receipt_ocr")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error",      "Receipt scanning requires a Pro or Premium subscription.",
                    "upgradeUrl", "/settings"
            ));
        }
        String currency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "USD";
        return ResponseEntity.ok(ocrService.processReceipt(image, currency, user));
    }

    // ── Auto-detect from bank notification (PRO+) ────────────────────────────

    /**
     * POST /api/transactions/auto-detect
     * Parses a bank/wallet notification text such as "You spent 1200 RSD at IDEA".
     * Does NOT save a transaction — returns parsed data for the frontend to review.
     * Requires PRO plan.
     *
     * Request body: {"rawText": "You spent 1200 RSD at IDEA"}
     */
    @PostMapping("/auto-detect")
    public ResponseEntity<?> autoDetect(@RequestBody Map<String, String> body) {
        User user = securityUtils.getCurrentUser();
        if (!planAccessService.hasAccess(user, "auto_detect")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error",      "Auto-detect requires a Pro or Premium subscription.",
                    "upgradeUrl", "/settings"
            ));
        }
        String rawText = body.get("rawText");
        if (rawText == null || rawText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "rawText is required"));
        }
        String currency = user.getBaseCurrency() != null ? user.getBaseCurrency() : "USD";
        return ResponseEntity.ok(textParserService.parseBankNotification(rawText, currency));
    }

    // ── Exception handler ─────────────────────────────────────────────────────

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
