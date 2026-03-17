package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    
    List<Budget> findByCategory(String category);
    
    List<Budget> findByIsActive(Boolean isActive);
    
    @Query("SELECT b FROM Budget b WHERE b.isActive = true AND :currentDate BETWEEN b.startDate AND b.endDate")
    List<Budget> findActiveBudgetsForDate(LocalDate currentDate);
    
    @Query("SELECT b FROM Budget b WHERE b.category = :category AND b.isActive = true AND :currentDate BETWEEN b.startDate AND b.endDate")
    Optional<Budget> findActiveBudgetByCategoryAndDate(String category, LocalDate currentDate);
    
    @Query("SELECT b FROM Budget b WHERE b.spentAmount >= (b.limitAmount * :threshold / 100) AND b.isActive = true")
    List<Budget> findBudgetsExceedingThreshold(double threshold);
}
