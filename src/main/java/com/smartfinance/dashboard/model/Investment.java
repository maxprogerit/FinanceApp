package com.smartfinance.dashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "investments")
@Data
public class Investment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String assetType; // STOCK, CRYPTO, BOND, MUTUAL_FUND, ETF
    
    @Column(nullable = false)
    private String symbol; // e.g., AAPL, BTC, etc.
    
    @Column(nullable = false)
    private String assetName;
    
    @Column(nullable = false)
    private BigDecimal quantity;
    
    @Column(nullable = false)
    private BigDecimal purchasePrice;
    
    private BigDecimal currentPrice;
    
    @Column(nullable = false)
    private String currency;
    
    @Column(nullable = false)
    private LocalDateTime purchaseDate;
    
    private String notes;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getTotalInvestment() {
        return purchasePrice.multiply(quantity);
    }
    
    public BigDecimal getCurrentValue() {
        if (currentPrice == null) {
            return getTotalInvestment();
        }
        return currentPrice.multiply(quantity);
    }
    
    public BigDecimal getProfitLoss() {
        return getCurrentValue().subtract(getTotalInvestment());
    }
    
    public double getProfitLossPercentage() {
        BigDecimal investment = getTotalInvestment();
        if (investment.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return getProfitLoss().divide(investment, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
