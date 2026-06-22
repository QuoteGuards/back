package com.project.back.domain.quote.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.project.back.domain.user.dto.UserStatsProjection;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.project.back.domain.quote.entity.QQuote.quote;
import static com.project.back.domain.customer.entity.QCustomer.customer;

@Repository
@RequiredArgsConstructor
public class QuoteRepositoryImpl implements QuoteRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // ── 상태 그룹 상수 ────────────────────────────────────────────────────

    /** 승인 완료 실적으로 집계되는 상태 (APPROVED·SENT·EXPIRED) */
    private static final List<QuoteStatus> APPROVED_STATUSES =
            List.of(QuoteStatus.APPROVED, QuoteStatus.SENT, QuoteStatus.EXPIRED);

    /** 발송 완료 실적으로 집계되는 상태 (SENT·EXPIRED) */
    private static final List<QuoteStatus> SENT_STATUSES =
            List.of(QuoteStatus.SENT, QuoteStatus.EXPIRED);

    /** 금액 합계 집계 대상 상태 (APPROVED·SENT·EXPIRED) */
    private static final List<QuoteStatus> AMOUNT_STATUSES =
            List.of(QuoteStatus.APPROVED, QuoteStatus.SENT, QuoteStatus.EXPIRED);

    /** 전체 견적 수 집계에서 제외하는 상태 */
    private static final List<QuoteStatus> EXCLUDED_STATUSES =
            List.of(QuoteStatus.DRAFT, QuoteStatus.CANCELLED);

    // ── 견적 목록 검색 ────────────────────────────────────────────────────

    @Override
    public List<Quote> searchMyQuotes(Long userId,
                                      QuoteStatus status,
                                      String customerName,
                                      String quoteNumber,
                                      LocalDateTime from,
                                      LocalDateTime to) {
        return queryFactory
                .selectFrom(quote)
                .join(quote.customer, customer).fetchJoin()
                .where(
                        eqUserId(userId),
                        eqStatus(status),
                        containsCustomerName(customerName),
                        containsQuoteNumber(quoteNumber),
                        betweenCreatedAt(from, to),
                        quote.isLatest.isTrue()
                )
                .orderBy(quote.createdAt.desc())
                .fetch();
    }

    // ── 통계 집계 ─────────────────────────────────────────────────────────

    /**
     * 사용자별 견적 통계를 DB에서 한 번의 집계 쿼리로 계산한다.
     *
     * <p>집계 기준:
     * <ul>
     *   <li>isLatest = true 인 최신 버전 견적만 대상 (각 견적 그룹의 최신 상태 반영)</li>
     *   <li>DRAFT · CANCELLED 상태 제외</li>
     *   <li>승인 완료: APPROVED · SENT · EXPIRED</li>
     *   <li>반려: REJECTED (최신 버전 기준, 재요청·재작성 시 자동 제거)</li>
     *   <li>발송 완료: SENT · EXPIRED</li>
     *   <li>금액 집계: APPROVED · SENT · EXPIRED</li>
     * </ul>
     *
     * <p>평균 계산:
     * <ul>
     *   <li>할인율 = SUM(discountAmount / subtotal * 100) / COUNT(subtotal > 0)</li>
     *   <li>이익률 = SUM(profitRate) / COUNT(profitRate NOT NULL)</li>
     *   <li>분모가 0이면 0 반환 (Java BigDecimal 처리)</li>
     * </ul>
     */
    @Override
    public UserStatsProjection aggregateUserStats(Long userId) {

        // ── 집계 표현식 정의 ────────────────────────────────────────────

        // 전체 건수 (isLatest=true, DRAFT·CANCELLED 제외)
        NumberExpression<Long> exprTotalQuotes = quote.id.count();

        // 승인 완료 건수
        NumberExpression<Long> exprApprovedQuotes = new CaseBuilder()
                .when(quote.status.in(APPROVED_STATUSES)).then(1L).otherwise(0L).sum();

        // 반려 건수 (최신 버전 기준)
        NumberExpression<Long> exprRejectedQuotes = new CaseBuilder()
                .when(quote.status.eq(QuoteStatus.REJECTED)).then(1L).otherwise(0L).sum();

        // 발송 완료 건수
        NumberExpression<Long> exprSentQuotes = new CaseBuilder()
                .when(quote.status.in(SENT_STATUSES)).then(1L).otherwise(0L).sum();

        // 총 견적 금액 (APPROVED·SENT·EXPIRED)
        NumberExpression<BigDecimal> exprTotalAmount = new CaseBuilder()
                .when(quote.status.in(AMOUNT_STATUSES))
                .then(quote.totalAmount).otherwise(BigDecimal.ZERO).sum();

        // 총 공급가액
        NumberExpression<BigDecimal> exprTotalSupplyAmount = new CaseBuilder()
                .when(quote.status.in(AMOUNT_STATUSES))
                .then(quote.supplyAmount).otherwise(BigDecimal.ZERO).sum();

        // 총 예상 이익금
        NumberExpression<BigDecimal> exprTotalProfitAmount = new CaseBuilder()
                .when(quote.status.in(AMOUNT_STATUSES))
                .then(quote.expectedProfitAmount).otherwise(BigDecimal.ZERO).sum();

        // 할인율 합계: SUM(discountAmount / subtotal * 100) — subtotal > 0 인 경우만
        NumberExpression<BigDecimal> exprDiscountRateSum = new CaseBuilder()
                .when(quote.subtotal.gt(BigDecimal.ZERO))
                .then(quote.discountAmount.coalesce(BigDecimal.ZERO)
                        .divide(quote.subtotal)
                        .multiply(BigDecimal.valueOf(100)))
                .otherwise(BigDecimal.ZERO).sum();

        // 할인율 유효 건수: COUNT(subtotal > 0)
        NumberExpression<Long> exprDiscountRateCount = new CaseBuilder()
                .when(quote.subtotal.gt(BigDecimal.ZERO)).then(1L).otherwise(0L).sum();

        // 이익률 합계·건수 (profitRate NOT NULL → DB의 nullable=false이므로 모두 포함)
        NumberExpression<BigDecimal> exprProfitRateSum = quote.profitRate.sum();
        NumberExpression<Long>      exprProfitRateCount = quote.profitRate.count();

        // ── 집계 쿼리 실행 ──────────────────────────────────────────────

        Tuple result = queryFactory
                .select(
                        exprTotalQuotes,
                        exprApprovedQuotes,
                        exprRejectedQuotes,
                        exprSentQuotes,
                        exprTotalAmount,
                        exprTotalSupplyAmount,
                        exprTotalProfitAmount,
                        exprDiscountRateSum,
                        exprDiscountRateCount,
                        exprProfitRateSum,
                        exprProfitRateCount
                )
                .from(quote)
                .where(
                        quote.createdBy.id.eq(userId),
                        quote.isLatest.isTrue(),
                        quote.status.notIn(EXCLUDED_STATUSES)
                )
                .fetchOne();

        if (result == null) {
            return UserStatsProjection.empty();
        }

        long totalQuotes = Objects.requireNonNullElse(result.get(exprTotalQuotes), 0L);
        if (totalQuotes == 0L) {
            return UserStatsProjection.empty();
        }

        return new UserStatsProjection(
                totalQuotes,
                Objects.requireNonNullElse(result.get(exprApprovedQuotes), 0L),
                Objects.requireNonNullElse(result.get(exprRejectedQuotes), 0L),
                Objects.requireNonNullElse(result.get(exprSentQuotes), 0L),
                Objects.requireNonNullElse(result.get(exprTotalAmount), BigDecimal.ZERO),
                Objects.requireNonNullElse(result.get(exprTotalSupplyAmount), BigDecimal.ZERO),
                Objects.requireNonNullElse(result.get(exprTotalProfitAmount), BigDecimal.ZERO),
                Objects.requireNonNullElse(result.get(exprDiscountRateSum), BigDecimal.ZERO),
                Objects.requireNonNullElse(result.get(exprDiscountRateCount), 0L),
                Objects.requireNonNullElse(result.get(exprProfitRateSum), BigDecimal.ZERO),
                Objects.requireNonNullElse(result.get(exprProfitRateCount), 0L)
        );
    }

    /**
     * 상태·버전 무관하게 한 건이라도 견적을 생성한 사용자 ID를 모두 조회한다.
     * 배치 재집계 대상 산출에 사용한다.
     */
    @Override
    public List<Long> findAllCreatedByUserIds() {
        return queryFactory
                .select(quote.createdBy.id)
                .from(quote)
                .distinct()
                .fetch();
    }

    // ── 검색 조건 헬퍼 ────────────────────────────────────────────────────

    private BooleanExpression eqUserId(Long userId) {
        return userId != null ? quote.createdBy.id.eq(userId) : null;
    }

    private BooleanExpression eqStatus(QuoteStatus status) {
        return status != null ? quote.status.eq(status) : null;
    }

    private BooleanExpression containsCustomerName(String customerName) {
        return (customerName != null && !customerName.isBlank())
                ? customer.companyName.containsIgnoreCase(customerName)
                : null;
    }

    private BooleanExpression containsQuoteNumber(String quoteNumber) {
        return (quoteNumber != null && !quoteNumber.isBlank())
                ? quote.quoteNumber.containsIgnoreCase(quoteNumber)
                : null;
    }

    private BooleanExpression betweenCreatedAt(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) return quote.createdAt.between(from, to);
        if (from != null) return quote.createdAt.goe(from);
        if (to != null) return quote.createdAt.loe(to);
        return null;
    }
}
