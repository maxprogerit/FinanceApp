package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Investment;
import com.smartfinance.dashboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    // ── User-scoped queries ───────────────────────────────────────────────────

    List<Investment> findByUser(User user);

    Optional<Investment> findByIdAndUser(Long id, User user);

    List<Investment> findByAssetTypeAndUser(String assetType, User user);

    @Query("SELECT i.assetType, SUM(i.quantity * i.purchasePrice) FROM Investment i WHERE i.user = :user GROUP BY i.assetType")
    List<Object[]> getTotalInvestmentByAssetTypeForUser(@Param("user") User user);

    @Query("SELECT SUM(i.quantity * i.purchasePrice) FROM Investment i WHERE i.user = :user")
    BigDecimal getTotalInvestmentValueForUser(@Param("user") User user);

    @Query("SELECT SUM(i.quantity * COALESCE(i.currentPrice, i.purchasePrice)) FROM Investment i WHERE i.user = :user")
    BigDecimal getCurrentPortfolioValueForUser(@Param("user") User user);

    // ── Legacy unscoped queries ───────────────────────────────────────────────

    List<Investment> findByAssetType(String assetType);

    List<Investment> findBySymbol(String symbol);

    @Query("SELECT i.assetType, SUM(i.quantity * i.purchasePrice) FROM Investment i GROUP BY i.assetType")
    List<Object[]> getTotalInvestmentByAssetType();

    @Query("SELECT SUM(i.quantity * i.purchasePrice) FROM Investment i")
    BigDecimal getTotalInvestmentValue();

    @Query("SELECT SUM(i.quantity * COALESCE(i.currentPrice, i.purchasePrice)) FROM Investment i")
    BigDecimal getCurrentPortfolioValue();
}
