package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Investment;
import com.smartfinance.dashboard.repository.InvestmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvestmentService {
    
    private final InvestmentRepository investmentRepository;
    
    @Transactional
    public Investment createInvestment(Investment investment) {
        if (investment.getCurrentPrice() == null) {
            investment.setCurrentPrice(investment.getPurchasePrice());
        }
        return investmentRepository.save(investment);
    }
    
    @Transactional
    public Investment updateInvestment(Long id, Investment investment) {
        Investment existing = investmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        existing.setAssetType(investment.getAssetType());
        existing.setSymbol(investment.getSymbol());
        existing.setAssetName(investment.getAssetName());
        existing.setQuantity(investment.getQuantity());
        existing.setPurchasePrice(investment.getPurchasePrice());
        existing.setCurrentPrice(investment.getCurrentPrice());
        existing.setCurrency(investment.getCurrency());
        existing.setPurchaseDate(investment.getPurchaseDate());
        existing.setNotes(investment.getNotes());
        
        return investmentRepository.save(existing);
    }
    
    @Transactional
    public void deleteInvestment(Long id) {
        investmentRepository.deleteById(id);
    }
    
    public Investment getInvestmentById(Long id) {
        return investmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
    }
    
    public List<Investment> getAllInvestments() {
        return investmentRepository.findAll();
    }
    
    public List<Investment> getInvestmentsByAssetType(String assetType) {
        return investmentRepository.findByAssetType(assetType);
    }
    
    public BigDecimal getTotalPortfolioValue() {
        BigDecimal value = investmentRepository.getCurrentPortfolioValue();
        return value != null ? value : BigDecimal.ZERO;
    }
    
    public BigDecimal getTotalInvestmentValue() {
        BigDecimal value = investmentRepository.getTotalInvestmentValue();
        return value != null ? value : BigDecimal.ZERO;
    }
    
    public BigDecimal getTotalProfitLoss() {
        return getTotalPortfolioValue().subtract(getTotalInvestmentValue());
    }
    
    public double getTotalProfitLossPercentage() {
        BigDecimal investment = getTotalInvestmentValue();
        if (investment.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return getTotalProfitLoss()
                .divide(investment, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
    
    public Map<String, BigDecimal> getPortfolioByAssetType() {
        List<Object[]> results = investmentRepository.getTotalInvestmentByAssetType();
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (BigDecimal) r[1]
                ));
    }
    
    @Transactional
    public void updateInvestmentPrice(Long id, BigDecimal newPrice) {
        Investment investment = getInvestmentById(id);
        investment.setCurrentPrice(newPrice);
        investmentRepository.save(investment);
    }
}
