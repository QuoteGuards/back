package com.project.back.domain.dashboard.service;

import com.project.back.domain.dashboard.dto.PeriodRange;
import com.project.back.domain.dashboard.dto.SummaryRow;
import com.project.back.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.project.back.domain.dashboard.repository.DashboardRepository;
import com.project.back.global.enums.QuoteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

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
