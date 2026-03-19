package com.smartfinance.dashboard.service;

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

    @Transactional
    public Transaction createTransaction(Transaction transaction, User user) {
        transaction.setUser(user);
        Transaction saved = transactionRepository.save(transaction);
        if ("EXPENSE".equals(transaction.getType())) {
            budgetService.updateBudgetSpending(
                    transaction.getCategory(), transaction.getAmount(), transaction.getCurrency(), user);
        }
        return saved;
    }

    @Transactional
    public Transaction updateTransaction(Long id, Transaction transaction, User user) {
        Transaction existing = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        BigDecimal oldAmount = existing.getAmount();
        String oldCategory = existing.getCategory();
        String oldCurrency = existing.getCurrency();
        String oldType = existing.getType();

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
