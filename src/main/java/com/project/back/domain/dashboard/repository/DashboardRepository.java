package com.project.back.domain.dashboard.repository;

import com.project.back.domain.dashboard.dto.DepartmentStatRow;
import com.project.back.domain.dashboard.dto.MonthlyTrendRow;
import com.project.back.domain.dashboard.dto.PopularProductRow;
import com.project.back.domain.dashboard.dto.SalesStaffRow;
import com.project.back.domain.dashboard.dto.StatusCountRow;
import com.project.back.domain.dashboard.dto.SummaryRow;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.global.enums.QuoteStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DashboardRepository extends JpaRepository<Quote, Long> {

    // 공통: 기간 필터(from/to) + 작성자 부서 필터(department, null이면 전체)
    @Query("""
            SELECT new com.project.back.domain.dashboard.dto.SummaryRow(
                COUNT(q),
                SUM(CASE WHEN q.status = :approved THEN 1L ELSE 0L END),
                SUM(CASE WHEN q.status = :rejected THEN 1L ELSE 0L END),
                SUM(CASE WHEN q.sentAt IS NOT NULL THEN 1L ELSE 0L END),
                SUM(q.totalAmount),
                SUM(q.supplyAmount),
                SUM(q.expectedProfitAmount),
                AVG(CASE WHEN q.subtotal > 0 THEN q.discountAmount / q.subtotal * 100 ELSE NULL END),
                AVG(q.profitRate))
            FROM Quote q
            JOIN q.createdBy u
            WHERE q.isLatest = true
              AND (:from IS NULL OR q.createdAt >= :from)
              AND (:to   IS NULL OR q.createdAt <= :to)
              AND (:department IS NULL OR u.department = :department)
            """)
    SummaryRow aggregateSummary(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("department") String department,
            @Param("approved") QuoteStatus approved,
            @Param("rejected") QuoteStatus rejected
    );

    // 월별 추이: 연·월 단위로 견적 수 / 총액 집계 (데이터 있는 월만 반환)
    @Query("""
            SELECT new com.project.back.domain.dashboard.dto.MonthlyTrendRow(
                YEAR(q.createdAt),
                MONTH(q.createdAt),
                COUNT(q),
                SUM(q.totalAmount))
            FROM Quote q
            JOIN q.createdBy u
            WHERE q.isLatest = true
              AND (:from IS NULL OR q.createdAt >= :from)
              AND (:to   IS NULL OR q.createdAt <= :to)
              AND (:department IS NULL OR u.department = :department)
            GROUP BY YEAR(q.createdAt), MONTH(q.createdAt)
            ORDER BY YEAR(q.createdAt), MONTH(q.createdAt)
            """)
    List<MonthlyTrendRow> aggregateMonthlyTrend(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("department") String department
    );

    // 견적 상태별 건수 (데이터 있는 상태만 반환 → Service에서 전체 상태 0 보정)
    @Query("""
            SELECT new com.project.back.domain.dashboard.dto.StatusCountRow(
                q.status,
                COUNT(q))
            FROM Quote q
            JOIN q.createdBy u
            WHERE q.isLatest = true
              AND (:from IS NULL OR q.createdAt >= :from)
              AND (:to   IS NULL OR q.createdAt <= :to)
              AND (:department IS NULL OR u.department = :department)
            GROUP BY q.status
            """)
    List<StatusCountRow> aggregateStatusCount(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("department") String department
    );

    // 인기 제품 순위: Product를 조인해 productId 기준 집계 (현재 제품명 사용, 스냅샷 이름 분리/중복 방지)
    // INNER JOIN이라 productId NULL(수동입력) 및 삭제된 제품은 자동 제외
    @Query("""
            SELECT new com.project.back.domain.dashboard.dto.PopularProductRow(
                p.id,
                p.name,
                COUNT(DISTINCT qi.quote.id),
                SUM(qi.quantity),
                SUM(qi.lineTotal))
            FROM QuoteItem qi
            JOIN Product p ON p.id = qi.productId
            JOIN qi.quote.createdBy u
            WHERE qi.quote.isLatest = true
              AND (:from IS NULL OR qi.quote.createdAt >= :from)
              AND (:to   IS NULL OR qi.quote.createdAt <= :to)
              AND (:department IS NULL OR u.department = :department)
            GROUP BY p.id, p.name
            ORDER BY COUNT(DISTINCT qi.quote.id) DESC, SUM(qi.quantity) DESC
            """)
    List<PopularProductRow> aggregatePopularProducts(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("department") String department,
            Pageable pageable
    );

    // 영업사원별: 작성자(createdBy)로 GROUP BY, 견적수/승인/반려 집계
    @Query("""
            SELECT new com.project.back.domain.dashboard.dto.SalesStaffRow(
                u.id,
                u.name,
                COUNT(q),
                SUM(CASE WHEN q.status = :approved THEN 1L ELSE 0L END),
                SUM(CASE WHEN q.status = :rejected THEN 1L ELSE 0L END))
            FROM Quote q
            JOIN q.createdBy u
            WHERE q.isLatest = true
              AND (:from IS NULL OR q.createdAt >= :from)
              AND (:to   IS NULL OR q.createdAt <= :to)
              AND (:department IS NULL OR u.department = :department)
            GROUP BY u.id, u.name
            ORDER BY COUNT(q) DESC
            """)
    List<SalesStaffRow> aggregateSalesStaff(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("department") String department,
            @Param("approved") QuoteStatus approved,
            @Param("rejected") QuoteStatus rejected
    );

    // 부서별: 작성자(createdBy)의 department로 GROUP BY, 견적수/승인/반려/총액 집계
    // (부서별 통계 패널 — 부서 필터와 무관하게 전 부서 표시)
    @Query("""
            SELECT new com.project.back.domain.dashboard.dto.DepartmentStatRow(
                u.department,
                COUNT(q),
                SUM(CASE WHEN q.status = :approved THEN 1L ELSE 0L END),
                SUM(CASE WHEN q.status = :rejected THEN 1L ELSE 0L END),
                SUM(q.totalAmount))
            FROM Quote q
            JOIN q.createdBy u
            WHERE q.isLatest = true
              AND (:from IS NULL OR q.createdAt >= :from)
              AND (:to   IS NULL OR q.createdAt <= :to)
            GROUP BY u.department
            ORDER BY COUNT(q) DESC
            """)
    List<DepartmentStatRow> aggregateDepartment(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("approved") QuoteStatus approved,
            @Param("rejected") QuoteStatus rejected
    );

    // 부서 필터 드롭다운용: 견적 작성자가 속한 부서 목록 (데이터 있는 부서만)
    @Query("""
            SELECT DISTINCT u.department
            FROM Quote q JOIN q.createdBy u
            WHERE q.isLatest = true
              AND u.department IS NOT NULL
            ORDER BY u.department
            """)
    List<String> findDistinctDepartments();
}
