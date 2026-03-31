package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Investment;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.InvestmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final MarketDataService    marketDataService;

    @Transactional
    public Investment createInvestment(Investment investment, User user) {
        investment.setUser(user);
        if (investment.getCurrentPrice() == null) {
            investment.setCurrentPrice(investment.getPurchasePrice());
        }
        return investmentRepository.save(investment);
    }

    @Transactional
    public Investment updateInvestment(Long id, Investment investment, User user) {
        Investment existing = investmentRepository.findByIdAndUser(id, user)
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
    public void deleteInvestment(Long id, User user) {
        investmentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        investmentRepository.deleteById(id);
    }

    public Investment getInvestmentById(Long id, User user) {
        return investmentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
    }

    public List<Investment> getAllInvestments(User user) {
        return investmentRepository.findByUser(user);
    }

    public List<Investment> getInvestmentsByAssetType(String assetType, User user) {
        return investmentRepository.findByAssetTypeAndUser(assetType, user);
    }

    public BigDecimal getTotalPortfolioValue(User user) {
        BigDecimal value = investmentRepository.getCurrentPortfolioValueForUser(user);
        return value != null ? value : BigDecimal.ZERO;
    }

    public BigDecimal getTotalInvestmentValue(User user) {
        BigDecimal value = investmentRepository.getTotalInvestmentValueForUser(user);
        return value != null ? value : BigDecimal.ZERO;
    }

    public BigDecimal getTotalProfitLoss(User user) {
        return getTotalPortfolioValue(user).subtract(getTotalInvestmentValue(user));
    }

    public double getTotalProfitLossPercentage(User user) {
        BigDecimal investment = getTotalInvestmentValue(user);
        if (investment.compareTo(BigDecimal.ZERO) == 0) return 0;
        return getTotalProfitLoss(user)
                .divide(investment, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public Map<String, BigDecimal> getPortfolioByAssetType(User user) {
        List<Object[]> results = investmentRepository.getTotalInvestmentByAssetTypeForUser(user);
        return results.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (BigDecimal) r[1]));
    }

    @Transactional
    public void updateInvestmentPrice(Long id, BigDecimal newPrice, User user) {
        Investment investment = getInvestmentById(id, user);
        investment.setCurrentPrice(newPrice);
        investmentRepository.save(investment);
    }

    /**
     * Fetches live market prices for all of a user's investments and persists
     * any new values. Symbols with no price data (API disabled or symbol unknown)
     * are silently skipped — existing prices are preserved.
     *
     * @return number of prices actually updated
     */
    @Transactional
    public int refreshPrices(User user) {
        List<Investment> investments = investmentRepository.findByUser(user);
        int updated = 0;
        for (Investment inv : investments) {
            try {
                BigDecimal fresh = marketDataService.getPrice(inv.getSymbol());
                if (fresh != null && fresh.compareTo(BigDecimal.ZERO) > 0) {
                    inv.setCurrentPrice(fresh);
                    investmentRepository.save(inv);
                    updated++;
                }
            } catch (Exception ex) {
                log.warn("Price refresh failed for symbol {}: {}", inv.getSymbol(), ex.getMessage());
            }
        }
        if (updated > 0) log.info("Refreshed {} investment price(s) for user {}", updated, user.getUsername());
        return updated;
    }
}
