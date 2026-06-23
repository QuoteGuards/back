package com.project.back.domain.dashboard.service;

import com.project.back.domain.dashboard.dto.MonthlyTrendRow;
import com.project.back.domain.dashboard.dto.PeriodRange;
import com.project.back.domain.dashboard.dto.PopularProductRow;
import com.project.back.domain.dashboard.dto.StatusCountRow;
import com.project.back.domain.dashboard.dto.SummaryRow;
import com.project.back.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.project.back.domain.dashboard.dto.response.MonthlyTrendResponse;
import com.project.back.domain.dashboard.dto.response.PopularProductResponse;
import com.project.back.domain.dashboard.dto.response.QuoteStatusCountResponse;
import com.project.back.domain.dashboard.repository.DashboardRepository;
import com.project.back.global.enums.QuoteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    // 견적 상태별 건수 (전체 상태를 0 포함하여 반환 → 차트 일관성)
    public List<QuoteStatusCountResponse> getQuoteStatusCount(String period, LocalDate from, LocalDate to) {
        PeriodRange range = PeriodRange.of(period, from, to);

        Map<QuoteStatus, Long> counts = new EnumMap<>(QuoteStatus.class);
        for (StatusCountRow row : dashboardRepository.aggregateStatusCount(range.from(), range.to())) {
            counts.put(row.status(), nzL(row.count()));
        }

        return java.util.Arrays.stream(QuoteStatus.values())
                .map(status -> QuoteStatusCountResponse.builder()
                        .status(status.name())
                        .count(counts.getOrDefault(status, 0L))
                        .build())
                .toList();
    }

    // 인기 제품 순위 (TOP N, 기간 필터)
    public List<PopularProductResponse> getPopularProducts(String period, LocalDate from, LocalDate to, int limit) {
        PeriodRange range = PeriodRange.of(period, from, to);

        return dashboardRepository.aggregatePopularProducts(
                        range.from(), range.to(), PageRequest.of(0, limit))
                .stream()
                .map(this::toPopularResponse)
                .toList();
    }

    private PopularProductResponse toPopularResponse(PopularProductRow row) {
        return PopularProductResponse.builder()
                .productId(row.productId())
                .productName(row.productName())
                .orderCount(nzL(row.orderCount()))
                .totalQuantity(nz(row.totalQuantity()))
                .totalSalesAmount(nz(row.totalSalesAmount()))
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
