package com.project.back.domain.dashboard.dto;

import java.math.BigDecimal;

// 월별 집계 projection (YEAR/MONTH 분리, Service에서 "yyyy-MM" 포맷)
public record MonthlyTrendRow(
        Integer year,
        Integer month,
        Long quoteCount,
        BigDecimal totalAmount
) {}
