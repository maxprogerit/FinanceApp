package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.FinancialGoal;
import com.smartfinance.dashboard.repository.FinancialGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialGoalService {
    
    private final FinancialGoalRepository goalRepository;
    private final AlertService alertService;
    
    @Transactional
    public FinancialGoal createGoal(FinancialGoal goal) {
        return goalRepository.save(goal);
    }
    
    @Transactional
    public FinancialGoal updateGoal(Long id, FinancialGoal goal) {
        FinancialGoal existing = goalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Financial goal not found"));
        
        existing.setGoalName(goal.getGoalName());
        existing.setDescription(goal.getDescription());
        existing.setTargetAmount(goal.getTargetAmount());
        existing.setCurrentAmount(goal.getCurrentAmount());
        existing.setCurrency(goal.getCurrency());
        existing.setTargetDate(goal.getTargetDate());
        existing.setCategory(goal.getCategory());
        existing.setStatus(goal.getStatus());
        
        // Check if goal is completed
        if (existing.getCurrentAmount().compareTo(existing.getTargetAmount()) >= 0) {
            existing.setStatus("COMPLETED");
            alertService.createGoalAlert(existing, "INFO", 
                    "Goal Achieved!", 
                    String.format("Congratulations! You've reached your goal: %s", existing.getGoalName()));
        }
        
        return goalRepository.save(existing);
    }
    
    @Transactional
    public void deleteGoal(Long id) {
        goalRepository.deleteById(id);
    }
    
    public FinancialGoal getGoalById(Long id) {
        return goalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Financial goal not found"));
    }
    
    public List<FinancialGoal> getAllGoals() {
        return goalRepository.findAll();
    }
    
    public List<FinancialGoal> getActiveGoals() {
        return goalRepository.findActiveGoalsOrderedByTargetDate();
    }
    
    public List<FinancialGoal> getOverdueGoals() {
        return goalRepository.findOverdueGoals();
    }
    
    @Transactional
    public FinancialGoal addToGoal(Long id, BigDecimal amount) {
        FinancialGoal goal = getGoalById(id);
        goal.setCurrentAmount(goal.getCurrentAmount().add(amount));
        
        // Check if goal is completed
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus("COMPLETED");
            alertService.createGoalAlert(goal, "INFO", 
                    "Goal Achieved!", 
                    String.format("Congratulations! You've reached your goal: %s", goal.getGoalName()));
        }
        
        return goalRepository.save(goal);
    }
    
    @Transactional
    public FinancialGoal withdrawFromGoal(Long id, BigDecimal amount) {
        FinancialGoal goal = getGoalById(id);
        BigDecimal newAmount = goal.getCurrentAmount().subtract(amount);
        
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Insufficient funds in goal");
        }
        
        goal.setCurrentAmount(newAmount);
        return goalRepository.save(goal);
    }
}
