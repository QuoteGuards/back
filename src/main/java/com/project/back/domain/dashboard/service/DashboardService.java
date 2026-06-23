package com.project.back.domain.dashboard.service;

import com.project.back.domain.dashboard.dto.MonthlyTrendRow;
import com.project.back.domain.dashboard.dto.PeriodRange;
import com.project.back.domain.dashboard.dto.SummaryRow;
import com.project.back.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.project.back.domain.dashboard.dto.response.MonthlyTrendResponse;
import com.project.back.domain.dashboard.repository.DashboardRepository;
import com.project.back.global.enums.QuoteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardSummaryResponse getSummary(String period, LocalDate from, LocalDate to) {
        PeriodRange range = PeriodRange.of(period, from, to);

        SummaryRow row = dashboardRepository.aggregateSummary(
                range.from(), range.to(), QuoteStatus.APPROVED, QuoteStatus.REJECTED);

        return DashboardSummaryResponse.builder()
                .totalQuotes(nzL(row.totalQuotes()))
                .approvedQuotes(nzL(row.approvedQuotes()))
                .rejectedQuotes(nzL(row.rejectedQuotes()))
                .sentQuotes(nzL(row.sentQuotes()))
                .totalAmount(nz(row.totalAmount()))
                .totalSupplyAmount(nz(row.totalSupplyAmount()))
                .totalProfitAmount(nz(row.totalProfitAmount()))
                .averageDiscountRate(toBd(row.averageDiscountRate()))
                .averageProfitRate(toBd(row.averageProfitRate()))
                .build();
    }

    // 월별 추이: 연·월 → "yyyy-MM" 포맷 변환
    public List<MonthlyTrendResponse> getMonthlyTrend(String period, LocalDate from, LocalDate to) {
        PeriodRange range = PeriodRange.of(period, from, to);

        return dashboardRepository.aggregateMonthlyTrend(range.from(), range.to())
                .stream()
                .map(this::toTrendResponse)
                .toList();
    }

    private MonthlyTrendResponse toTrendResponse(MonthlyTrendRow row) {
        return MonthlyTrendResponse.builder()
                .month(String.format("%04d-%02d", row.year(), row.month()))
                .quoteCount(nzL(row.quoteCount()))
                .totalAmount(nz(row.totalAmount()))
                .build();
    }

    private long nzL(Long v) {
        return v != null ? v : 0L;
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal toBd(Double d) {
        return d == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    }
}
