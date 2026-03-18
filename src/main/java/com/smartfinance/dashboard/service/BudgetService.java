package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Budget;
import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.repository.BudgetRepository;
import com.smartfinance.dashboard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final AlertService alertService;
    private final TransactionRepository transactionRepository;
    private final CurrencyService currencyService;

    /** Convert amount from one currency to another using live/cached rates. */
    private BigDecimal convertToBudgetCurrency(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null || fromCurrency.equals(toCurrency)) return amount;
        Map<String, Double> rates = currencyService.getRatesFromUSD();
        double fromRate = rates.getOrDefault(fromCurrency, 1.0);
        double toRate   = rates.getOrDefault(toCurrency,   1.0);
        return amount
                .divide(BigDecimal.valueOf(fromRate), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(toRate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Budget createBudget(Budget budget) {
        return budgetRepository.save(budget);
    }

    @Transactional
    public Budget updateBudget(Long id, Budget budget) {
        Budget existing = budgetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        existing.setCategory(budget.getCategory());
        existing.setLimitAmount(budget.getLimitAmount());
        existing.setPeriod(budget.getPeriod());
        existing.setStartDate(budget.getStartDate());
        existing.setEndDate(budget.getEndDate());
        existing.setCurrency(budget.getCurrency());
        if (budget.getIsActive() != null) {
            existing.setIsActive(budget.getIsActive());
        }

        return budgetRepository.save(existing);
    }

    @Transactional
    public void deleteBudget(Long id) {
        budgetRepository.deleteById(id);
    }

    public Budget getBudgetById(Long id) {
        return budgetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
    }

    public List<Budget> getAllBudgets() {
        return budgetRepository.findAll();
    }

    public List<Budget> getActiveBudgets() {
        return budgetRepository.findActiveBudgetsForDate(LocalDate.now());
    }

    @Transactional
    public void updateBudgetSpending(String category, BigDecimal amount, String transactionCurrency) {
        budgetRepository.findActiveBudgetByCategoryAndDate(category, LocalDate.now())
                .ifPresent(budget -> {
                    String budgetCurrency = budget.getCurrency() != null ? budget.getCurrency() : "USD";
                    BigDecimal converted = convertToBudgetCurrency(amount, transactionCurrency, budgetCurrency);
                    budget.setSpentAmount(budget.getSpentAmount().add(converted));
                    budgetRepository.save(budget);

                    double percentageUsed = budget.getPercentageUsed();
                    if (percentageUsed >= 90 && percentageUsed < 100) {
                        alertService.createBudgetAlert(budget, "WARNING",
                                "Budget Alert: 90% Limit Reached",
                                String.format("Your %s budget is at %.1f%% of its limit.",
                                        category, percentageUsed));
                    } else if (percentageUsed >= 100) {
                        alertService.createBudgetAlert(budget, "CRITICAL",
                                "Budget Exceeded!",
                                String.format("Your %s budget has exceeded its limit by %s.",
                                        category, budget.getRemainingAmount().abs()));
                    }
                });
    }

    /**
     * Recomputes spentAmount for all budgets by summing actual EXPENSE transactions
     * within each budget's date range and category, converting each transaction's
     * currency to the budget's currency. Fixes drift caused by manual DB edits or migration.
     */
    @Transactional
    public int recalculateAllBudgetSpending() {
        List<Budget> budgets = budgetRepository.findAll();
        for (Budget budget : budgets) {
            LocalDateTime start = budget.getStartDate().atStartOfDay();
            LocalDateTime end   = budget.getEndDate().atTime(23, 59, 59, 999999999);
            String budgetCurrency = budget.getCurrency() != null ? budget.getCurrency() : "USD";

            List<Transaction> expenses = transactionRepository
                    .findByTypeAndTransactionDateBetween("EXPENSE", start, end)
                    .stream()
                    .filter(t -> budget.getCategory().equals(t.getCategory()))
                    .toList();

            BigDecimal totalSpent = BigDecimal.ZERO;
            for (Transaction tx : expenses) {
                totalSpent = totalSpent.add(
                        convertToBudgetCurrency(tx.getAmount(), tx.getCurrency(), budgetCurrency));
            }

            budget.setSpentAmount(totalSpent);
            budgetRepository.save(budget);
        }
        return budgets.size();
    }

    public List<Budget> getBudgetsExceedingThreshold(double threshold) {
        return budgetRepository.findBudgetsExceedingThreshold(threshold);
    }
}
