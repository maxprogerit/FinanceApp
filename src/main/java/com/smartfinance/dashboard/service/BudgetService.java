package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Budget;
import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.model.User;
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
    public Budget createBudget(Budget budget, User user) {
        budget.setUser(user);
        return budgetRepository.save(budget);
    }

    @Transactional
    public Budget updateBudget(Long id, Budget budget, User user) {
        Budget existing = budgetRepository.findByIdAndUser(id, user)
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
    public void deleteBudget(Long id, User user) {
        budgetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
        budgetRepository.deleteById(id);
    }

    public Budget getBudgetById(Long id, User user) {
        return budgetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
    }

    public List<Budget> getAllBudgets(User user) {
        return budgetRepository.findByUser(user);
    }

    public List<Budget> getActiveBudgets(User user) {
        return budgetRepository.findActiveBudgetsForDateAndUser(LocalDate.now(), user);
    }

    @Transactional
    public void updateBudgetSpending(String category, BigDecimal amount, String transactionCurrency, User user) {
        budgetRepository.findActiveBudgetByCategoryAndDateAndUser(category, LocalDate.now(), user)
                .ifPresent(budget -> {
                    String budgetCurrency = budget.getCurrency() != null ? budget.getCurrency() : "USD";
                    BigDecimal converted = convertToBudgetCurrency(amount, transactionCurrency, budgetCurrency);
                    budget.setSpentAmount(budget.getSpentAmount().add(converted));
                    budgetRepository.save(budget);

                    double pct = budget.getPercentageUsed();
                    if (pct >= 90 && pct < 100) {
                        alertService.createBudgetAlert(budget, user, "WARNING",
                                "Budget Alert: 90% Limit Reached",
                                String.format("Your %s budget is at %.1f%% of its limit.", category, pct));
                    } else if (pct >= 100) {
                        alertService.createBudgetAlert(budget, user, "CRITICAL",
                                "Budget Exceeded!",
                                String.format("Your %s budget has exceeded its limit by %s.",
                                        category, budget.getRemainingAmount().abs()));
                    }
                });
    }

    @Transactional
    public int recalculateAllBudgetSpending(User user) {
        List<Budget> budgets = budgetRepository.findByUser(user);
        for (Budget budget : budgets) {
            LocalDateTime start = budget.getStartDate().atStartOfDay();
            LocalDateTime end   = budget.getEndDate().atTime(23, 59, 59, 999999999);
            String budgetCurrency = budget.getCurrency() != null ? budget.getCurrency() : "USD";

            List<Transaction> expenses = transactionRepository
                    .findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, start, end)
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

    public List<Budget> getBudgetsExceedingThreshold(double threshold, User user) {
        return budgetRepository.findBudgetsExceedingThresholdForUser(threshold, user);
    }
}
