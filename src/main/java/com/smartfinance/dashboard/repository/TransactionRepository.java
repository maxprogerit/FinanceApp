package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // ── User-scoped queries (use these in production) ────────────────────────

    List<Transaction> findByUser(User user);

    Optional<Transaction> findByIdAndUser(Long id, User user);

    List<Transaction> findByTypeAndUser(String type, User user);

    List<Transaction> findByCategoryAndUser(String category, User user);

    List<Transaction> findByUserAndTransactionDateBetween(User user, LocalDateTime startDate, LocalDateTime endDate);

    List<Transaction> findByTypeAndUserAndTransactionDateBetween(
            String type, User user, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT SUM(t.amount) FROM Transaction t " +
           "WHERE t.user = :user AND t.type = :type " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByUserAndTypeAndDateRange(
            @Param("user") User user,
            @Param("type") String type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t " +
           "WHERE t.user = :user AND t.type = :type " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY t.category")
    List<Object[]> sumAmountByUserAndTypeAndCategoryAndDateRange(
            @Param("user") User user,
            @Param("type") String type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM Transaction t WHERE t.user = :user AND t.isRecurring = true AND t.type = :type")
    List<Transaction> findRecurringTransactionsByTypeAndUser(
            @Param("type") String type,
            @Param("user") User user);

    // ── Legacy unscoped queries (kept for BudgetService.recalculateAll) ──────

    List<Transaction> findByType(String type);

    List<Transaction> findByCategory(String category);

    List<Transaction> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Transaction> findByTypeAndTransactionDateBetween(String type, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.type = :type AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByTypeAndDateRange(String type, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t WHERE t.type = :type AND t.transactionDate BETWEEN :startDate AND :endDate GROUP BY t.category")
    List<Object[]> sumAmountByTypeAndCategoryAndDateRange(String type, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT SUM(t.amount) FROM Transaction t " +
           "WHERE t.type = 'EXPENSE' AND t.category = :category " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumExpensesByCategoryAndDateRange(
            @Param("category") String category,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM Transaction t WHERE t.isRecurring = true AND t.type = :type")
    List<Transaction> findRecurringTransactionsByType(String type);

    @Query("SELECT t.storageType, SUM(t.amount) FROM Transaction t " +
           "WHERE t.storageType IS NOT NULL AND t.storageType <> '' AND t.type = 'INCOME' " +
           "GROUP BY t.storageType")
    List<Object[]> sumIncomeByStorageType();

    @Query("SELECT t.storageType, SUM(t.amount) FROM Transaction t " +
           "WHERE t.storageType IS NOT NULL AND t.storageType <> '' AND t.type = 'EXPENSE' " +
           "GROUP BY t.storageType")
    List<Object[]> sumExpensesByStorageType();

    @Query("SELECT t.incomeSource, SUM(t.amount) FROM Transaction t " +
           "WHERE t.type = 'INCOME' AND t.incomeSource IS NOT NULL AND t.incomeSource <> '' " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY t.incomeSource")
    List<Object[]> getIncomeBySourceForPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    long countByStorageType(String storageType);

    long countByIncomeSource(String incomeSource);
}
