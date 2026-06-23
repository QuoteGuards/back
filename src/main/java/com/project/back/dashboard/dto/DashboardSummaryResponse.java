package com.project.back.dashboard.dto;


import java.math.BigDecimal;

public record DashboardSummaryResponse(
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
}
