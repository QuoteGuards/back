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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private QuoteItem mockItem(String discountRate, DiscountPolicy policy) {
        QuoteItem item = mock(QuoteItem.class);
        when(item.getDiscountRate()).thenReturn(new BigDecimal(discountRate));
        when(item.getDiscountPolicy()).thenReturn(policy);
        return item;
    }

    @Nested
    @DisplayName("품목 policy가 없는 경우")
    class NoPolicyTests {

        @Test
        @DisplayName("모든 품목 policy null이면 승인 불필요 (빈 리스트 반환)")
        void nullPolicy_returnsEmptyList() {
            QuoteItem item = mock(QuoteItem.class);
            when(item.getDiscountRate()).thenReturn(new BigDecimal("20"));
            when(item.getDiscountPolicy()).thenReturn(null);

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(item),
                    new BigDecimal("5000000"),
                    new BigDecimal("10")
            );

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("DISCOUNT_EXCEEDED - 할인율 초과")
    class DiscountExceededTests {

        @Test
        @DisplayName("항목 할인율이 해당 품목 policy 최대치 초과 시 DISCOUNT_EXCEEDED 반환")
        void discountExceeded() {
            DiscountPolicy policy = mockPolicy("10", "15", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(mockItem("15", policy)),
                    new BigDecimal("1000000"),
                    new BigDecimal("20")
            );

            assertThat(result).contains(ApprovalReasonType.DISCOUNT_EXCEEDED);
        }

        @Test
        @DisplayName("할인율이 품목 policy 최대치 이하이면 DISCOUNT_EXCEEDED 미포함")
        void discountNotExceeded() {
            DiscountPolicy policy = mockPolicy("10", "15", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(mockItem("10", policy)),
                    new BigDecimal("1000000"),
                    new BigDecimal("20")
            );

            assertThat(result).doesNotContain(ApprovalReasonType.DISCOUNT_EXCEEDED);
        }

        @Test
        @DisplayName("품목마다 다른 policy — 각 품목 기준으로만 할인 초과 판단")
        void perItemPolicy_discountExceeded() {
            DiscountPolicy strict = mockPolicy("10", "20", "100000000");
            DiscountPolicy lenient = mockPolicy("20", "10", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(
                            mockItem("12", strict),
                            mockItem("15", lenient)
                    ),
                    new BigDecimal("1000000"),
                    new BigDecimal("18")
            );

            assertThat(result).contains(ApprovalReasonType.DISCOUNT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("LOW_PROFIT - 이익률 미달")
    class LowProfitTests {

        @Test
        @DisplayName("견적 이익률이 품목 policy 중 가장 높은 minProfitRate 미만이면 LOW_PROFIT 반환")
        void profitRateLow() {
            DiscountPolicy policy = mockPolicy("10", "15", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(mockItem("5", policy)),
                    new BigDecimal("1000000"),
                    new BigDecimal("10")
            );

            assertThat(result).contains(ApprovalReasonType.LOW_PROFIT);
        }

        @Test
        @DisplayName("복수 policy 중 strictest minProfitRate(20%) 기준으로 LOW_PROFIT 판단")
        void profitRateLow_usesStrictestMinAmongItems() {
            DiscountPolicy strict = mockPolicy("10", "20", "100000000");
            DiscountPolicy lenient = mockPolicy("20", "10", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(
                            mockItem("5", strict),
                            mockItem("5", lenient)
                    ),
                    new BigDecimal("1000000"),
                    new BigDecimal("15")
            );

            assertThat(result).contains(ApprovalReasonType.LOW_PROFIT);
        }

        @Test
        @DisplayName("이익률이 strictest minProfitRate 이상이면 LOW_PROFIT 미포함")
        void profitRateOk() {
            DiscountPolicy policy = mockPolicy("10", "15", "100000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(mockItem("5", policy)),
                    new BigDecimal("1000000"),
                    new BigDecimal("20")
            );

            assertThat(result).doesNotContain(ApprovalReasonType.LOW_PROFIT);
        }
    }

    @Nested
    @DisplayName("HIGH_AMOUNT - 고액 견적")
    class HighAmountTests {

        @Test
        @DisplayName("총금액이 품목 policy 중 가장 낮은 threshold 이상이면 HIGH_AMOUNT 반환")
        void highAmount() {
            DiscountPolicy policy = mockPolicy("10", "15", "10000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(mockItem("5", policy)),
                    new BigDecimal("15000000"),
                    new BigDecimal("20")
            );

            assertThat(result).contains(ApprovalReasonType.HIGH_AMOUNT);
        }

        @Test
        @DisplayName("복수 policy 중 strictest threshold(3천만) 기준으로 HIGH_AMOUNT 판단")
        void highAmount_usesStrictestThresholdAmongItems() {
            DiscountPolicy strict = mockPolicy("10", "15", "30000000");
            DiscountPolicy lenient = mockPolicy("10", "15", "50000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(
                            mockItem("5", strict),
                            mockItem("5", lenient)
                    ),
                    new BigDecimal("40000000"),
                    new BigDecimal("20")
            );

            assertThat(result).contains(ApprovalReasonType.HIGH_AMOUNT);
        }

        @Test
        @DisplayName("총금액이 strictest threshold 미만이면 HIGH_AMOUNT 미포함")
        void amountOk() {
            DiscountPolicy policy = mockPolicy("10", "15", "10000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(mockItem("5", policy)),
                    new BigDecimal("5000000"),
                    new BigDecimal("20")
            );

            assertThat(result).doesNotContain(ApprovalReasonType.HIGH_AMOUNT);
        }
    }

    @Nested
    @DisplayName("복수 승인 사유 동시 반환")
    class MultipleReasonsTests {

        @Test
        @DisplayName("할인 초과 + 이익률 미달 + 고액 세 가지 동시 해당")
        void allThreeReasons() {
            DiscountPolicy policy = mockPolicy("10", "15", "10000000");

            List<ApprovalReasonType> result = approvalCheckService.check(
                    List.of(mockItem("20", policy)),
                    new BigDecimal("15000000"),
                    new BigDecimal("5")
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
                    List.of(mockItem("5", policy)),
                    new BigDecimal("5000000"),
                    new BigDecimal("20")
            );

            assertThat(result).isEmpty();
        }
    }
}
