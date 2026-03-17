package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
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
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("startDate") LocalDateTime startDate,
            @org.springframework.data.repository.query.Param("endDate") LocalDateTime endDate);

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
            @org.springframework.data.repository.query.Param("startDate") LocalDateTime startDate,
            @org.springframework.data.repository.query.Param("endDate") LocalDateTime endDate);

    long countByStorageType(String storageType);

    long countByIncomeSource(String incomeSource);
}
