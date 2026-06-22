package com.project.back.domain.user.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * QueryDSL 통계 집계 결과를 담는 Projection DTO.
 *
 * <p>평균 할인율·이익률의 분자(sum)와 유효 건수(count)를 분리하여 전달하고,
 * 최종 나눗셈과 HALF_UP 반올림은 Java BigDecimal로 처리한다.</p>
 */
public record UserStatsProjection(
        long totalQuotes,
        long approvedQuotes,
        long rejectedQuotes,
        long sentQuotes,
        BigDecimal totalAmount,
        BigDecimal totalSupplyAmount,
        BigDecimal totalProfitAmount,
        /** SUM( discountAmount / subtotal * 100 ) — subtotal > 0 인 행만 포함 */
        BigDecimal discountRateSum,
        /** COUNT( subtotal > 0 ) */
        long discountRateCount,
        /** SUM( profitRate ) — NOT NULL 행만 포함 */
        BigDecimal profitRateSum,
        /** COUNT( profitRate ) — NOT NULL 행 수 */
        long profitRateCount
) {

    public static UserStatsProjection empty() {
        return new UserStatsProjection(
                0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L
        );
    }

    /** 평균 할인율 계산. 유효 견적이 없으면 0 반환. */
    public BigDecimal calcAverageDiscountRate() {
        if (discountRateCount == 0 || discountRateSum == null) return BigDecimal.ZERO;
        return discountRateSum.divide(BigDecimal.valueOf(discountRateCount), 2, RoundingMode.HALF_UP);
    }

    /** 평균 이익률 계산. 유효 견적이 없으면 0 반환. */
    public BigDecimal calcAverageProfitRate() {
        if (profitRateCount == 0 || profitRateSum == null) return BigDecimal.ZERO;
        return profitRateSum.divide(BigDecimal.valueOf(profitRateCount), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal safeAmount(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO);
    }
}
