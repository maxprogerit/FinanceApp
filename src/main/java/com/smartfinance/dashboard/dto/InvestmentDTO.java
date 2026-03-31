package com.smartfinance.dashboard.dto;

import com.smartfinance.dashboard.model.Investment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API response DTO for investments.
 * Exposes all entity fields plus computed profit/loss analytics so callers
 * never have to recalculate on the client side.
 */
public record InvestmentDTO(
        Long           id,
        String         symbol,
        String         assetName,
        String         assetType,
        BigDecimal     quantity,
        BigDecimal     purchasePrice,
        BigDecimal     currentPrice,
        String         currency,
        LocalDateTime  purchaseDate,
        String         notes,
        BigDecimal     totalInvestment,
        BigDecimal     currentValue,
        BigDecimal     profitLoss,
        double         profitLossPercentage,
        LocalDateTime  createdAt,
        LocalDateTime  updatedAt
) {
    /** Factory method — builds a DTO from a persisted Investment entity. */
    public static InvestmentDTO from(Investment inv) {
        return new InvestmentDTO(
                inv.getId(),
                inv.getSymbol(),
                inv.getAssetName(),
                inv.getAssetType(),
                inv.getQuantity(),
                inv.getPurchasePrice(),
                inv.getCurrentPrice(),
                inv.getCurrency(),
                inv.getPurchaseDate(),
                inv.getNotes(),
                inv.getTotalInvestment(),
                inv.getCurrentValue(),
                inv.getProfitLoss(),
                inv.getProfitLossPercentage(),
                inv.getCreatedAt(),
                inv.getUpdatedAt()
        );
    }
}
