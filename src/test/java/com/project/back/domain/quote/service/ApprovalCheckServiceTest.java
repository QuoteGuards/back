package com.project.back.domain.quote.service;

import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.global.enums.ApprovalReasonType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("ApprovalCheckService 단위 테스트")
class ApprovalCheckServiceTest {

    private ApprovalCheckService approvalCheckService;

    @BeforeEach
    void setUp() {
        approvalCheckService = new ApprovalCheckService();
    }

    private DiscountPolicy mockPolicy(String maxDiscountRate,
                                      String minProfitRate,
                                      String thresholdAmount) {
        DiscountPolicy policy = mock(DiscountPolicy.class);
        when(policy.getMaxDiscountRate()).thenReturn(new BigDecimal(maxDiscountRate));
        when(policy.getMinProfitRate()).thenReturn(new BigDecimal(minProfitRate));
        when(policy.getApprovalThresholdAmount()).thenReturn(new BigDecimal(thresholdAmount));
        return policy;
    }

    private QuoteItem mockItem(String discountRate) {
        QuoteItem item = mock(QuoteItem.class);
        when(item.getDiscountRate()).thenReturn(new BigDecimal(discountRate));
        return item;
    }

    // ── 정책 없음 ──────────────────────────────────────

    @Nested
    @DisplayName("할인 정책이 null인 경우")
    class NoPolicyTests {

        @Test
        @DisplayName("정책 null이면 승인 불필요 (빈 리스트 반환)")
        void nullPolicy_returnsEmptyList() {
            List<ApprovalReasonType> result = approvalCheckService.check(
                    null,
                    List.of(mockItem("20")),
                    new BigDecimal("5000000"),
                    new BigDecimal("10")
            );

            assertThat(result).isEmpty();
        }
    }

    // ── 할인율 초과 ────────────────────────────────────

    @Nested
    @DisplayName("DISCOUNT_EXCEEDED - 할인율 초과")
    class DiscountExceededTests {

        @Test
        @DisplayName("항목 할인율이 정책 최대치 초과 시 DISCOUNT_EXCEEDED 반환")
        void discountExceeded() {
            DiscountPolicy policy = mockPolicy("10", "15", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    policy,
                    List.of(mockItem("15")),  // 15% > 10%
                    new BigDecimal("1000000"),
                    new BigDecimal("20")
            );

            assertThat(result).contains(ApprovalReasonType.DISCOUNT_EXCEEDED);
        }

        @Test
        @DisplayName("할인율이 정책 최대치 이하이면 DISCOUNT_EXCEEDED 미포함")
        void discountNotExceeded() {
            DiscountPolicy policy = mockPolicy("10", "15", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    policy,
                    List.of(mockItem("10")),  // 10% == 10% (초과 아님)
                    new BigDecimal("1000000"),
                    new BigDecimal("20")
            );

            assertThat(result).doesNotContain(ApprovalReasonType.DISCOUNT_EXCEEDED);
        }
    }

    // ── 이익률 미달 ────────────────────────────────────

    @Nested
    @DisplayName("LOW_PROFIT - 이익률 미달")
    class LowProfitTests {

        @Test
        @DisplayName("이익률이 정책 최소치 미만이면 LOW_PROFIT 반환")
        void profitRateLow() {
            DiscountPolicy policy = mockPolicy("10", "15", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    policy,
                    List.of(mockItem("5")),
                    new BigDecimal("1000000"),
                    new BigDecimal("10")   // 10% < 15%
            );

            assertThat(result).contains(ApprovalReasonType.LOW_PROFIT);
        }

        @Test
        @DisplayName("이익률이 정책 최소치 이상이면 LOW_PROFIT 미포함")
        void profitRateOk() {
            DiscountPolicy policy = mockPolicy("10", "15", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    policy,
                    List.of(mockItem("5")),
                    new BigDecimal("1000000"),
                    new BigDecimal("20")   // 20% > 15%
            );

            assertThat(result).doesNotContain(ApprovalReasonType.LOW_PROFIT);
        }
    }

    // ── 고액 견적 ──────────────────────────────────────

    @Nested
    @DisplayName("HIGH_AMOUNT - 고액 견적")
    class HighAmountTests {

        @Test
        @DisplayName("총금액이 기준 초과 시 HIGH_AMOUNT 반환")
        void highAmount() {
            DiscountPolicy policy = mockPolicy("10", "15", "10000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    policy,
                    List.of(mockItem("5")),
                    new BigDecimal("15000000"),  // 15,000,000 > 10,000,000
                    new BigDecimal("20")
            );

            assertThat(result).contains(ApprovalReasonType.HIGH_AMOUNT);
        }

        @Test
        @DisplayName("총금액이 기준 이하이면 HIGH_AMOUNT 미포함")
        void amountOk() {
            DiscountPolicy policy = mockPolicy("10", "15", "10000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    policy,
                    List.of(mockItem("5")),
                    new BigDecimal("5000000"),   // 5,000,000 < 10,000,000
                    new BigDecimal("20")
            );

            assertThat(result).doesNotContain(ApprovalReasonType.HIGH_AMOUNT);
        }
    }

    // ── 복수 사유 ──────────────────────────────────────

    @Nested
    @DisplayName("복수 승인 사유 동시 반환")
    class MultipleReasonsTests {

        @Test
        @DisplayName("할인 초과 + 이익률 미달 + 고액 세 가지 동시 해당")
        void allThreeReasons() {
            DiscountPolicy policy = mockPolicy("10", "15", "10000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    policy,
                    List.of(mockItem("20")),     // 할인율 20% > 10%
                    new BigDecimal("15000000"),  // 고액
                    new BigDecimal("5")          // 이익률 5% < 15%
            );

            assertThat(result).containsExactlyInAnyOrder(
                    ApprovalReasonType.DISCOUNT_EXCEEDED,
                    ApprovalReasonType.LOW_PROFIT,
                    ApprovalReasonType.HIGH_AMOUNT
            );
        }

        @Test
        @DisplayName("아무 조건도 해당 안 되면 빈 리스트 반환 (승인 불필요)")
        void noReasons() {
            DiscountPolicy policy = mockPolicy("10", "15", "10000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    policy,
                    List.of(mockItem("5")),     // 할인율 5% < 10%
                    new BigDecimal("5000000"),  // 기준 이하
                    new BigDecimal("20")        // 이익률 20% > 15%
            );

            assertThat(result).isEmpty();
        }
    }
}
