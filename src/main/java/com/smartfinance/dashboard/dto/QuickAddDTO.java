package com.smartfinance.dashboard.dto;

import java.math.BigDecimal;

/**
 * Minimal payload for the POST /api/transactions/quick endpoint.
 * The server fills in user, date, and currency automatically.
 *
 * @param amount      Absolute (positive) transaction amount — required.
 * @param type        "INCOME" or "EXPENSE" — parsed by the frontend or backend.
 * @param category    Optional; server applies categorization rules if null.
 * @param description Optional free-text description.
 */
public record QuickAddDTO(
        BigDecimal amount,
        String type,
        String category,
        String description
) {}
