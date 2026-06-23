package com.project.back.domain.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 월별 추이 (월별 견적 수 / 월별 총액 / 추이 그래프용)
@Getter
@Builder
public class MonthlyTrendResponse {
    private String month;          // "2026-01"
    private long quoteCount;       // 월별 견적 수
    private BigDecimal totalAmount; // 월별 견적 총액(VAT 포함)
}
