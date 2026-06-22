package com.project.back.domain.user.dto;

import java.math.BigDecimal;

/**
 * QuoteRepository 집계 쿼리 결과를 담는 DTO
 */
public record UserStatsAggregation(
        long totalQuotes,
        long approvedQuotes,
        long rejectedQuotes,
        long sentQuotes,
        BigDecimal totalAmount,
        BigDecimal totalSupplyAmount,
        BigDecimal totalProfitAmount,
        BigDecimal averageDiscountRate,
        BigDecimal averageProfitRate
) {
    public static UserStatsAggregation empty() {
        return new UserStatsAggregation(0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
