package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Transaction;
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
    public Transaction createTransaction(Transaction transaction) {
        Transaction saved = transactionRepository.save(transaction);
        
        // Update budget if it's an expense
        if ("EXPENSE".equals(transaction.getType())) {
            budgetService.updateBudgetSpending(transaction.getCategory(), transaction.getAmount());
        }
        
        return saved;
    }
    
    @Transactional
    public Transaction updateTransaction(Long id, Transaction transaction) {
        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        
        BigDecimal oldAmount = existing.getAmount();
        String oldCategory = existing.getCategory();
        
        existing.setType(transaction.getType());
        existing.setAmount(transaction.getAmount());
        existing.setCurrency(transaction.getCurrency());
        existing.setCategory(transaction.getCategory());
        existing.setDescription(transaction.getDescription());
        existing.setTransactionDate(transaction.getTransactionDate());
        existing.setIsRecurring(transaction.getIsRecurring());
        existing.setRecurringFrequency(transaction.getRecurringFrequency());
        existing.setTags(transaction.getTags());
        
        Transaction updated = transactionRepository.save(existing);
        
        // Update budgets if amounts or categories changed
        if ("EXPENSE".equals(transaction.getType())) {
            if (!oldCategory.equals(transaction.getCategory())) {
                budgetService.updateBudgetSpending(oldCategory, oldAmount.negate());
                budgetService.updateBudgetSpending(transaction.getCategory(), transaction.getAmount());
            } else if (!oldAmount.equals(transaction.getAmount())) {
                BigDecimal difference = transaction.getAmount().subtract(oldAmount);
                budgetService.updateBudgetSpending(transaction.getCategory(), difference);
            }
        }
        
        return updated;
    }
    
    @Transactional
    public void deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        
        // Reduce budget spending if it's an expense
        if ("EXPENSE".equals(transaction.getType())) {
            budgetService.updateBudgetSpending(transaction.getCategory(), transaction.getAmount().negate());
        }
        
        transactionRepository.deleteById(id);
    }
    
    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
    }
    
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }
    
    public List<Transaction> getTransactionsByType(String type) {
        return transactionRepository.findByType(type);
    }
    
    public List<Transaction> getTransactionsByCategory(String category) {
        return transactionRepository.findByCategory(category);
    }
    
    public List<Transaction> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.findByTransactionDateBetween(startDate, endDate);
    }
    
    public BigDecimal getTotalIncomeForPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal total = transactionRepository.sumAmountByTypeAndDateRange("INCOME", startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }
    
    public BigDecimal getTotalExpensesForPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal total = transactionRepository.sumAmountByTypeAndDateRange("EXPENSE", startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }
    
    public Map<String, BigDecimal> getExpensesByCategory(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = transactionRepository
                .sumAmountByTypeAndCategoryAndDateRange("EXPENSE", startDate, endDate);
        
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (BigDecimal) r[1]
                ));
    }
    
    public Map<String, BigDecimal> getIncomesByCategory(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = transactionRepository
                .sumAmountByTypeAndCategoryAndDateRange("INCOME", startDate, endDate);
        
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (BigDecimal) r[1]
                ));
    }
}
