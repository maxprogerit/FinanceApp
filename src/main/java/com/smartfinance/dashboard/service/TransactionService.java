package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.dto.QuickAddDTO;
import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;
    private final CategorizationRuleService categorizationRuleService;

    @Transactional
    public Transaction createTransaction(Transaction transaction, User user) {
        transaction.setUser(user);
        if (transaction.getCurrency() == null || transaction.getCurrency().isBlank()) {
            String base = user.getBaseCurrency();
            transaction.setCurrency((base != null && !base.isBlank()) ? base : "EUR");
        }
        Transaction saved = transactionRepository.save(transaction);
        if ("EXPENSE".equals(transaction.getType())) {
            budgetService.updateBudgetSpending(
                    transaction.getCategory(), transaction.getAmount(), transaction.getCurrency(), user);
        }
        return saved;
    }

    /**
     * Quick-add a transaction from minimal input.
     * Server fills in: user, date (now), currency (user's baseCurrency).
     * Category is auto-detected from description when not provided.
     */
    @Transactional
    public Transaction quickAdd(QuickAddDTO dto, User user) {
        String type     = dto.type()   != null ? dto.type().toUpperCase() : "EXPENSE";
        String base     = user.getBaseCurrency();
        String currency = (base != null && !base.isBlank()) ? base : "EUR";
        CategorizationRuleService.RuleMatch match = categorizationRuleService.resolveRule(dto.description(), null, user);

        String category = (dto.category() != null && !dto.category().isBlank())
                ? dto.category()
                : match.category();
        if (category == null || category.isBlank()) {
            category = "Other";
        }

        if (match.transactionType() != null) {
            type = match.transactionType();
        }

        Transaction t = new Transaction();
        t.setUser(user);
        t.setType(type);
        t.setAmount(dto.amount().abs());
        t.setCurrency(currency);
        t.setCategory(category);
        t.setDescription(dto.description());
        t.setTransactionDate(LocalDateTime.now());
        t.setIsRecurring(false);

        Transaction saved = transactionRepository.save(t);
        if ("EXPENSE".equals(type)) {
            budgetService.updateBudgetSpending(category, t.getAmount(), currency, user);
        }
        return saved;
    }

    @Transactional
    public Transaction updateTransaction(Long id, Transaction transaction, User user) {
        Transaction existing = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        BigDecimal oldAmount   = existing.getAmount();
        String     oldCategory = existing.getCategory();
        String     oldCurrency = existing.getCurrency();
        String     oldType     = existing.getType();

        existing.setType(transaction.getType());
        existing.setAmount(transaction.getAmount());
        existing.setCurrency(transaction.getCurrency());
        existing.setCategory(transaction.getCategory());
        existing.setDescription(transaction.getDescription());
        existing.setTransactionDate(transaction.getTransactionDate());
        existing.setIsRecurring(transaction.getIsRecurring());
        existing.setRecurringFrequency(transaction.getRecurringFrequency());
        existing.setTags(transaction.getTags());
        existing.setStorageType(transaction.getStorageType());
        existing.setIncomeSource(transaction.getIncomeSource());

        Transaction updated = transactionRepository.save(existing);

        // Reverse old expense contribution, apply new one
        if ("EXPENSE".equals(oldType)) {
            budgetService.updateBudgetSpending(oldCategory, oldAmount.negate(), oldCurrency, user);
        }
        if ("EXPENSE".equals(transaction.getType())) {
            budgetService.updateBudgetSpending(
                    transaction.getCategory(), transaction.getAmount(), transaction.getCurrency(), user);
        }

        // Track category correction for auto-learn — REQUIRES_NEW transaction,
        // any failure here must not roll back the main update.
        if (transaction.getDescription() != null && transaction.getCategory() != null) {
            try {
                categorizationRuleService.maybeAutoLearn(
                        transaction.getDescription(), transaction.getCategory(), user);
            } catch (Exception ignored) { /* non-critical */ }
        }

        return updated;
    }

    @Transactional
    public void deleteTransaction(Long id, User user) {
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        if ("EXPENSE".equals(transaction.getType())) {
            budgetService.updateBudgetSpending(
                    transaction.getCategory(), transaction.getAmount().negate(), transaction.getCurrency(), user);
        }
        transactionRepository.deleteById(id);
    }

    public Transaction getTransactionById(Long id, User user) {
        return transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
    }

    public List<Transaction> getAllTransactions(User user) {
        return transactionRepository.findByUser(user);
    }

    public List<Transaction> getTransactionsByType(String type, User user) {
        return transactionRepository.findByTypeAndUser(type, user);
    }

    public List<Transaction> getTransactionsByCategory(String category, User user) {
        return transactionRepository.findByCategoryAndUser(category, user);
    }

    public List<Transaction> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate, User user) {
        return transactionRepository.findByUserAndTransactionDateBetween(user, startDate, endDate);
    }

    public BigDecimal getTotalIncomeForPeriod(LocalDateTime startDate, LocalDateTime endDate, User user) {
        BigDecimal total = transactionRepository.sumAmountByUserAndTypeAndDateRange(user, "INCOME", startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal getTotalExpensesForPeriod(LocalDateTime startDate, LocalDateTime endDate, User user) {
        BigDecimal total = transactionRepository.sumAmountByUserAndTypeAndDateRange(user, "EXPENSE", startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    public Map<String, BigDecimal> getExpensesByCategory(LocalDateTime startDate, LocalDateTime endDate, User user) {
        List<Object[]> results = transactionRepository
                .sumAmountByUserAndTypeAndCategoryAndDateRange(user, "EXPENSE", startDate, endDate);
        return results.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (BigDecimal) r[1]));
    }

    public Map<String, BigDecimal> getIncomesByCategory(LocalDateTime startDate, LocalDateTime endDate, User user) {
        List<Object[]> results = transactionRepository
                .sumAmountByUserAndTypeAndCategoryAndDateRange(user, "INCOME", startDate, endDate);
        return results.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (BigDecimal) r[1]));
    }
}
