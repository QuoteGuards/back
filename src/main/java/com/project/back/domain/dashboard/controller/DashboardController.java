package com.project.back.domain.dashboard.controller;

import com.project.back.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.project.back.domain.dashboard.dto.response.MonthlyTrendResponse;
import com.project.back.domain.dashboard.dto.response.PopularProductResponse;
import com.project.back.domain.dashboard.dto.response.QuoteStatusCountResponse;
import com.project.back.domain.dashboard.dto.response.SalesStaffResponse;
import com.project.back.domain.dashboard.service.DashboardService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAnyRole('SALES_MANAGER', 'SUPER_ADMIN')")
// 최고관리자, 영업관리자만 접근
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // 요약 카드: 견적수·금액·승인/반려/발송 건수·평균 할인율·평균 이익률
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(
            @RequestParam(required = false) String period,  // ONE_MONTH/THREE_MONTHS/SIX_MONTHS/CUSTOM
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "대시보드 요약 조회 성공",
                dashboardService.getSummary(period, from, to)
        ));
    }

    // 월별 추이: 월별 견적 수 / 총액 (추이 그래프용)
    @GetMapping("/monthly-trend")
    public ResponseEntity<ApiResponse<List<MonthlyTrendResponse>>> getMonthlyTrend(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "월별 추이 조회 성공",
                dashboardService.getMonthlyTrend(period, from, to)
        ));
    }

    // 견적 상태별 건수
    @GetMapping("/quote-status")
    public ResponseEntity<ApiResponse<List<QuoteStatusCountResponse>>> getQuoteStatusCount(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "견적 상태별 건수 조회 성공",
                dashboardService.getQuoteStatusCount(period, from, to)
        ));
    }

    // 인기 제품 순위 (TOP N)
    @GetMapping("/popular-products")
    public ResponseEntity<ApiResponse<List<PopularProductResponse>>> getPopularProducts(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "인기 제품 순위 조회 성공",
                dashboardService.getPopularProducts(period, from, to, limit)
        ));
    }

    // 영업사원별 통계: 작성건수 / 승인율 / 반려율
    @GetMapping("/sales-staff")
    public ResponseEntity<ApiResponse<List<SalesStaffResponse>>> getSalesStaff(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "영업사원별 통계 조회 성공",
                dashboardService.getSalesStaff(period, from, to)
        ));
    }
}
