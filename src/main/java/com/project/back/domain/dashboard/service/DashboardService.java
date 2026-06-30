package com.project.back.domain.dashboard.service;

import com.project.back.domain.dashboard.dto.DepartmentStatRow;
import com.project.back.domain.dashboard.dto.MonthlyTrendRow;
import com.project.back.domain.dashboard.dto.PeriodRange;
import com.project.back.domain.dashboard.dto.PopularProductRow;
import com.project.back.domain.dashboard.dto.ProductViewRankRow;
import com.project.back.domain.dashboard.dto.SalesStaffRow;
import com.project.back.domain.dashboard.dto.StatusCountRow;
import com.project.back.domain.dashboard.dto.SummaryRow;
import com.project.back.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.project.back.domain.dashboard.dto.response.DepartmentStatResponse;
import com.project.back.domain.dashboard.dto.response.MonthlyTrendResponse;
import com.project.back.domain.dashboard.dto.response.PopularProductResponse;
import com.project.back.domain.dashboard.dto.response.ProductViewRankResponse;
import com.project.back.domain.dashboard.dto.response.QuoteStatusCountResponse;
import com.project.back.domain.dashboard.dto.response.SalesStaffResponse;
import com.project.back.domain.dashboard.repository.DashboardRepository;
import com.project.back.domain.dashboard.dto.response.SalesAnalysisResponse;
import com.project.back.global.enums.QuoteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardSummaryResponse getSummary(String period, LocalDate from, LocalDate to, String department) {
        PeriodRange range = PeriodRange.of(period, from, to);

        SummaryRow row = dashboardRepository.aggregateSummary(
                range.from(), range.to(), nb(department), QuoteStatus.APPROVED, QuoteStatus.REJECTED);

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

    // 영업 현황 분석 문구
    public SalesAnalysisResponse getSalesAnalysis(String period, LocalDate from, LocalDate to, String department) {
        PeriodRange range = PeriodRange.of(period, from, to);

        SummaryRow row = dashboardRepository.aggregateSummary(
                range.from(), range.to(), nb(department), QuoteStatus.APPROVED, QuoteStatus.REJECTED);

        long totalQuotes = nzL(row.totalQuotes());
        long approvedQuotes = nzL(row.approvedQuotes());
        long rejectedQuotes = nzL(row.rejectedQuotes());
        long sentQuotes = nzL(row.sentQuotes());

        BigDecimal totalAmount = nz(row.totalAmount());
        BigDecimal totalProfitAmount = nz(row.totalProfitAmount());
        BigDecimal averageDiscountRate = toBd(row.averageDiscountRate());
        BigDecimal averageProfitRate = toBd(row.averageProfitRate());

        // 승인율/반려율 분모 = 실제 심사받은 건(승인+반려). 임시저장·승인불필요 등 미심사 건 제외
        long reviewedQuotes = approvedQuotes + rejectedQuotes;
        BigDecimal approvalRate = ratio(approvedQuotes, reviewedQuotes);
        BigDecimal rejectionRate = ratio(rejectedQuotes, reviewedQuotes);

        return SalesAnalysisResponse.builder()
                .totalQuotes(totalQuotes)
                .approvedQuotes(approvedQuotes)
                .rejectedQuotes(rejectedQuotes)
                .sentQuotes(sentQuotes)
                .totalAmount(totalAmount)
                .totalProfitAmount(totalProfitAmount)
                .averageDiscountRate(averageDiscountRate)
                .averageProfitRate(averageProfitRate)
                .approvalRate(approvalRate)
                .rejectionRate(rejectionRate)
                .summary(createSalesSummary(totalQuotes, totalAmount, averageProfitRate, approvalRate, rejectionRate))
                .recommendation(createSalesRecommendation(totalQuotes, averageDiscountRate, averageProfitRate, rejectionRate))
                .build();
    }

    private String createSalesSummary(
            long totalQuotes,
            BigDecimal totalAmount,
            BigDecimal averageProfitRate,
            BigDecimal approvalRate,
            BigDecimal rejectionRate
    ) {
        if (totalQuotes == 0) {
            return "선택한 기간에 등록된 견적 데이터가 없습니다.";
        }

        return "선택한 기간 동안 총 " + totalQuotes + "건의 견적이 등록되었고, "
                + "총 견적 금액은 " + wonText(totalAmount) + "원입니다. "
                + "평균 이익률은 " + averageProfitRate + "%, "
                + "승인율은 " + approvalRate + "%, "
                + "반려율은 " + rejectionRate + "%입니다.";
    }

    // 금액 문구용: 천단위 콤마 + 소수점 제거 (예: 5000000.00 → 5,000,000)
    // Locale 고정(KOREA)으로 런타임 환경과 무관하게 콤마 구분자 보장
    private String wonText(BigDecimal v) {
        if (v == null) return "0";
        return String.format(Locale.KOREA, "%,d", v.setScale(0, RoundingMode.HALF_UP).toBigInteger());
    }

    private String createSalesRecommendation(
            long totalQuotes,
            BigDecimal averageDiscountRate,
            BigDecimal averageProfitRate,
            BigDecimal rejectionRate
    ) {
        if (totalQuotes == 0) {
            return "견적 데이터가 쌓이면 할인율, 이익률, 승인/반려 비율을 기준으로 영업 현황을 분석할 수 있습니다.";
        }

        if (averageProfitRate.compareTo(BigDecimal.valueOf(10)) < 0) {
            return "평균 이익률이 낮습니다. 원가 구조와 할인 조건을 우선 검토하는 것이 좋습니다.";
        }

        if (rejectionRate.compareTo(BigDecimal.valueOf(30)) >= 0) {
            return "반려율이 높은 편입니다. 승인 요청 전에 할인율과 승인 필요 사유를 다시 점검하는 것이 좋습니다.";
        }

        if (averageDiscountRate.compareTo(BigDecimal.valueOf(20)) >= 0) {
            return "평균 할인율이 높은 편입니다. 고할인 견적에 대한 내부 검토 기준을 강화하는 것이 좋습니다.";
        }

        return "전체 영업 흐름은 안정적인 편입니다. 현재 수준의 이익률과 승인율을 유지하는 것이 좋습니다.";
    }


    // 월별 추이: 연·월 → "yyyy-MM" 포맷 변환
    public List<MonthlyTrendResponse> getMonthlyTrend(String period, LocalDate from, LocalDate to, String department) {
        PeriodRange range = PeriodRange.of(period, from, to);

        List<MonthlyTrendResponse> data = dashboardRepository.aggregateMonthlyTrend(range.from(), range.to(), nb(department))
                .stream()
                .map(this::toTrendResponse)
                .toList();

        // 기간이 정해진 경우(전체 제외) 데이터 없는 달을 0으로 채워 추이 연속성 확보
        if (range.from() == null) return data;

        Map<String, MonthlyTrendResponse> byMonth = data.stream()
                .collect(Collectors.toMap(MonthlyTrendResponse::getMonth, r -> r, (a, b) -> a, LinkedHashMap::new));

        YearMonth start = YearMonth.from(range.from());
        YearMonth end = range.to() != null ? YearMonth.from(range.to()) : YearMonth.now();

        List<MonthlyTrendResponse> filled = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            String key = String.format("%04d-%02d", m.getYear(), m.getMonthValue());
            filled.add(byMonth.getOrDefault(key, MonthlyTrendResponse.builder()
                    .month(key).quoteCount(0L).totalAmount(BigDecimal.ZERO).build()));
        }
        return filled;
    }

    private MonthlyTrendResponse toTrendResponse(MonthlyTrendRow row) {
        return MonthlyTrendResponse.builder()
                .month(String.format("%04d-%02d", row.year(), row.month()))
                .quoteCount(nzL(row.quoteCount()))
                .totalAmount(nz(row.totalAmount()))
                .build();
    }

    // 견적 상태별 건수 (전체 상태를 0 포함하여 반환 → 차트 일관성)
    public List<QuoteStatusCountResponse> getQuoteStatusCount(String period, LocalDate from, LocalDate to, String department) {
        PeriodRange range = PeriodRange.of(period, from, to);

        Map<QuoteStatus, Long> counts = new EnumMap<>(QuoteStatus.class);
        for (StatusCountRow row : dashboardRepository.aggregateStatusCount(range.from(), range.to(), nb(department))) {
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
    public List<PopularProductResponse> getPopularProducts(String period, LocalDate from, LocalDate to, String department, int limit) {
        PeriodRange range = PeriodRange.of(period, from, to);
        int safeLimit = Math.min(Math.max(limit, 1), 100);  // 1~100 보정 (PageRequest 예외 방지)

        return dashboardRepository.aggregatePopularProducts(
                        range.from(), range.to(), nb(department), PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toPopularResponse)
                .toList();
    }

    // 조회수 기반 인기 제품 순위 (TOP N, 누적 — 기간/부서 필터 무관)
    public List<ProductViewRankResponse> getTopProductsByViews(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return dashboardRepository.aggregateTopByViews(PageRequest.of(0, safeLimit))
                .stream()
                .map(r -> ProductViewRankResponse.builder()
                        .productId(r.productId())
                        .productName(r.productName())
                        .viewCount(r.viewCount() != null ? r.viewCount() : 0L)
                        .build())
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

    // 영업사원별 통계 (작성건수/승인율/반려율)
    public List<SalesStaffResponse> getSalesStaff(String period, LocalDate from, LocalDate to, String department) {
        PeriodRange range = PeriodRange.of(period, from, to);

        return dashboardRepository.aggregateSalesStaff(
                        range.from(), range.to(), nb(department), QuoteStatus.APPROVED, QuoteStatus.REJECTED)
                .stream()
                .map(this::toSalesStaffResponse)
                .toList();
    }

    // 부서 필터 드롭다운 목록 (견적 데이터가 있는 부서)
    public List<String> getDepartments() {
        return dashboardRepository.findDistinctDepartments();
    }

    // 부서별 통계 (작성자 department 기준 — 작성건수/승인율/반려율/총액)
    public List<DepartmentStatResponse> getDepartmentStats(String period, LocalDate from, LocalDate to) {
        PeriodRange range = PeriodRange.of(period, from, to);

        return dashboardRepository.aggregateDepartment(
                        range.from(), range.to(), QuoteStatus.APPROVED, QuoteStatus.REJECTED)
                .stream()
                .map(this::toDepartmentResponse)
                .toList();
    }

    private DepartmentStatResponse toDepartmentResponse(DepartmentStatRow row) {
        long total = nzL(row.totalQuotes());
        long approved = nzL(row.approvedQuotes());
        long rejected = nzL(row.rejectedQuotes());
        long reviewed = approved + rejected;

        String dept = (row.department() != null && !row.department().isBlank()) ? row.department() : "미지정";

        return DepartmentStatResponse.builder()
                .department(dept)
                .totalQuotes(total)
                .approvedQuotes(approved)
                .rejectedQuotes(rejected)
                .approvalRate(ratio(approved, reviewed))
                .rejectionRate(ratio(rejected, reviewed))
                .totalAmount(nz(row.totalAmount()))
                .build();
    }

    private SalesStaffResponse toSalesStaffResponse(SalesStaffRow row) {
        long total = nzL(row.totalQuotes());
        long approved = nzL(row.approvedQuotes());
        long rejected = nzL(row.rejectedQuotes());
        long reviewed = approved + rejected; // 분모 = 심사받은 건(승인+반려)

        return SalesStaffResponse.builder()
                .userId(row.userId())
                .userName(row.userName())
                .totalQuotes(total)
                .approvedQuotes(approved)
                .rejectedQuotes(rejected)
                .approvalRate(ratio(approved, reviewed))
                .rejectionRate(ratio(rejected, reviewed))
                .build();
    }

    // 비율(%) 계산, 0 나눗셈 방어
    private BigDecimal ratio(long part, long total) {
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    // 빈 문자열 → null (부서 미선택 = 전체)
    private String nb(String s) {
        return (s == null || s.isBlank()) ? null : s;
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
