package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Budget;
import com.smartfinance.dashboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    // ── User-scoped queries ───────────────────────────────────────────────────

    List<Budget> findByUser(User user);

    Optional<Budget> findByIdAndUser(Long id, User user);

    @Query("SELECT b FROM Budget b WHERE b.user = :user AND b.isActive = true AND :currentDate BETWEEN b.startDate AND b.endDate")
    List<Budget> findActiveBudgetsForDateAndUser(@Param("currentDate") LocalDate currentDate, @Param("user") User user);

    @Query("SELECT b FROM Budget b WHERE b.user = :user AND b.category = :category AND b.isActive = true AND :currentDate BETWEEN b.startDate AND b.endDate")
    Optional<Budget> findActiveBudgetByCategoryAndDateAndUser(
            @Param("category") String category,
            @Param("currentDate") LocalDate currentDate,
            @Param("user") User user);

    @Query("SELECT b FROM Budget b WHERE b.user = :user AND b.spentAmount >= (b.limitAmount * :threshold / 100) AND b.isActive = true")
    List<Budget> findBudgetsExceedingThresholdForUser(
            @Param("threshold") double threshold,
            @Param("user") User user);

    // ── Legacy unscoped queries ───────────────────────────────────────────────

    List<Budget> findByCategory(String category);

    List<Budget> findByIsActive(Boolean isActive);

    @Query("SELECT b FROM Budget b WHERE b.isActive = true AND :currentDate BETWEEN b.startDate AND b.endDate")
    List<Budget> findActiveBudgetsForDate(LocalDate currentDate);

    @Query("SELECT b FROM Budget b WHERE b.category = :category AND b.isActive = true AND :currentDate BETWEEN b.startDate AND b.endDate")
    Optional<Budget> findActiveBudgetByCategoryAndDate(String category, LocalDate currentDate);

    @Query("SELECT b FROM Budget b WHERE b.spentAmount >= (b.limitAmount * :threshold / 100) AND b.isActive = true")
    List<Budget> findBudgetsExceedingThreshold(double threshold);
}
