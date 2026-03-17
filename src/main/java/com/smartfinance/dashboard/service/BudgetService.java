package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Budget;
import com.smartfinance.dashboard.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {
    
    private final BudgetRepository budgetRepository;
    private final AlertService alertService;
    
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
        existing.setIsActive(budget.getIsActive());
        
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
    public void updateBudgetSpending(String category, BigDecimal amount) {
        budgetRepository.findActiveBudgetByCategoryAndDate(category, LocalDate.now())
                .ifPresent(budget -> {
                    budget.setSpentAmount(budget.getSpentAmount().add(amount));
                    budgetRepository.save(budget);
                    
                    // Check for budget warnings
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
    
    public List<Budget> getBudgetsExceedingThreshold(double threshold) {
        return budgetRepository.findBudgetsExceedingThreshold(threshold);
    }
}
