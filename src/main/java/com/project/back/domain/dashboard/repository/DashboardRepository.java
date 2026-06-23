package com.project.back.domain.dashboard.repository;

import com.project.back.domain.dashboard.dto.MonthlyTrendRow;
import com.project.back.domain.dashboard.dto.SummaryRow;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.global.enums.QuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DashboardRepository extends JpaRepository<Quote, Long> {

    @Query("""
            SELECT new com.project.back.domain.dashboard.dto.SummaryRow(
                COUNT(q),
                SUM(CASE WHEN q.status = :approved THEN 1L ELSE 0L END),
                SUM(CASE WHEN q.status = :rejected THEN 1L ELSE 0L END),
                SUM(CASE WHEN q.sentAt IS NOT NULL THEN 1L ELSE 0L END),
                SUM(q.totalAmount),
                SUM(q.supplyAmount),
                SUM(q.expectedProfitAmount),
                AVG(CASE WHEN q.subtotal > 0 THEN q.discountAmount / q.subtotal * 100 ELSE 0 END),
                AVG(q.profitRate))
            FROM Quote q
            WHERE q.isLatest = true
              AND (:from IS NULL OR q.createdAt >= :from)
              AND (:to   IS NULL OR q.createdAt <= :to)
            """)
    SummaryRow aggregateSummary(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
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
            WHERE q.isLatest = true
              AND (:from IS NULL OR q.createdAt >= :from)
              AND (:to   IS NULL OR q.createdAt <= :to)
            GROUP BY YEAR(q.createdAt), MONTH(q.createdAt)
            ORDER BY YEAR(q.createdAt), MONTH(q.createdAt)
            """)
    List<MonthlyTrendRow> aggregateMonthlyTrend(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
