package com.project.back.domain.dashboard.dto;

import java.math.BigDecimal;

// JPQL 집계 projection (Repository → Service 전달용)
// JPQL 집계 결과는 행이 없으면 SUM/AVG가 null → 전부 래퍼 타입으로 받고 Service에서 null 처리
public record SummaryRow(
        Long totalQuotes,
        Long approvedQuotes,
        Long rejectedQuotes,
        Long sentQuotes,
        BigDecimal totalAmount,
        BigDecimal totalSupplyAmount,
        BigDecimal totalProfitAmount,
        Double averageDiscountRate,   // AVG → Double
        Double averageProfitRate
) {}
