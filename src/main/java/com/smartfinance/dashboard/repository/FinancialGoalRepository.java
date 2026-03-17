package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.FinancialGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, Long> {
    
    List<FinancialGoal> findByStatus(String status);
    
    List<FinancialGoal> findByCategory(String category);
    
    @Query("SELECT g FROM FinancialGoal g WHERE g.status = 'IN_PROGRESS' ORDER BY g.targetDate ASC")
    List<FinancialGoal> findActiveGoalsOrderedByTargetDate();
    
    @Query("SELECT g FROM FinancialGoal g WHERE g.status = 'IN_PROGRESS' AND g.targetDate < CURRENT_DATE")
    List<FinancialGoal> findOverdueGoals();
}
