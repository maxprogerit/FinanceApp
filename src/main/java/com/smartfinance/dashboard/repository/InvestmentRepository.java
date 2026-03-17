package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Investment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    
    List<Investment> findByAssetType(String assetType);
    
    List<Investment> findBySymbol(String symbol);
    
    @Query("SELECT i.assetType, SUM(i.quantity * i.purchasePrice) FROM Investment i GROUP BY i.assetType")
    List<Object[]> getTotalInvestmentByAssetType();
    
    @Query("SELECT SUM(i.quantity * i.purchasePrice) FROM Investment i")
    BigDecimal getTotalInvestmentValue();
    
    @Query("SELECT SUM(i.quantity * COALESCE(i.currentPrice, i.purchasePrice)) FROM Investment i")
    BigDecimal getCurrentPortfolioValue();
}
