package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.FinancialGoal;
import com.smartfinance.dashboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, Long> {

    // ── User-scoped queries ───────────────────────────────────────────────────

    List<FinancialGoal> findByUser(User user);

    Optional<FinancialGoal> findByIdAndUser(Long id, User user);

    List<FinancialGoal> findByUserAndStatus(User user, String status);

    @Query("SELECT g FROM FinancialGoal g WHERE g.user = :user AND g.status = 'IN_PROGRESS' ORDER BY g.targetDate ASC")
    List<FinancialGoal> findActiveGoalsOrderedByTargetDateForUser(@Param("user") User user);

    @Query("SELECT g FROM FinancialGoal g WHERE g.user = :user AND g.status = 'IN_PROGRESS' AND g.targetDate < CURRENT_DATE")
    List<FinancialGoal> findOverdueGoalsForUser(@Param("user") User user);

    // ── Legacy unscoped queries ───────────────────────────────────────────────

    List<FinancialGoal> findByStatus(String status);

    List<FinancialGoal> findByCategory(String category);

    @Query("SELECT g FROM FinancialGoal g WHERE g.status = 'IN_PROGRESS' ORDER BY g.targetDate ASC")
    List<FinancialGoal> findActiveGoalsOrderedByTargetDate();

    @Query("SELECT g FROM FinancialGoal g WHERE g.status = 'IN_PROGRESS' AND g.targetDate < CURRENT_DATE")
    List<FinancialGoal> findOverdueGoals();
}
