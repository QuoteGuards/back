package com.project.back.domain.quote.service;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("QuoteCalculationService 단위 테스트")
class QuoteCalculationServiceTest {

    private QuoteCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new QuoteCalculationService();
    }

    // ── 헬퍼 메서드 ───────────────────────────────────

    private Quote mockQuote() {
        Quote quote = mock(Quote.class);
        doNothing().when(quote).updateCalculation(
                any(), any(), any(), any(), any(), any(), any(), any());
        return quote;
    }

    private QuoteItem buildItem(String unitPrice, String costPrice,
                                String quantity, String discountRate,
                                boolean vatApplicable) {
        return QuoteItem.builder()
                .productName("테스트 상품")
                .unitPrice(new BigDecimal(unitPrice))
                .costPrice(new BigDecimal(costPrice))
                .quantity(new BigDecimal(quantity))
                .discountRate(new BigDecimal(discountRate))
                .vatApplicable(vatApplicable)
                .build();
    }

    // ── 항목별 계산 ───────────────────────────────────

    @Nested
    @DisplayName("항목별 금액 계산")
    class ItemCalculation {

        @Test
        @DisplayName("할인 없음, VAT 적용 - 기본 계산")
        void noDiscount_withVat() {
            // given: 단가 100,000 * 수량 1 = 기준금액 100,000
            //        할인 0 → 공급가 100,000 → VAT 10,000 → 합계 110,000
            QuoteItem item = buildItem("100000", "70000", "1", "0", true);
            Quote quote = mockQuote();

            // when
            calculationService.calculate(quote, List.of(item));

            // then
            assertThat(item.getDiscountAmount()).isEqualByComparingTo("0.00");
            assertThat(item.getLineSupplyAmount()).isEqualByComparingTo("100000.00");
            assertThat(item.getVatAmount()).isEqualByComparingTo("10000.00");
            assertThat(item.getLineTotal()).isEqualByComparingTo("110000.00");
        }

        @Test
        @DisplayName("할인 10% 적용, VAT 포함")
        void withDiscount_withVat() {
            // given: 단가 200,000 * 수량 2 = 기준금액 400,000
            //        할인 10% = 40,000 → 공급가 360,000 → VAT 36,000 → 합계 396,000
            QuoteItem item = buildItem("200000", "150000", "2", "10", true);
            Quote quote = mockQuote();

            // when
            calculationService.calculate(quote, List.of(item));

            // then
            assertThat(item.getDiscountAmount()).isEqualByComparingTo("40000.00");
            assertThat(item.getLineSupplyAmount()).isEqualByComparingTo("360000.00");
            assertThat(item.getVatAmount()).isEqualByComparingTo("36000.00");
            assertThat(item.getLineTotal()).isEqualByComparingTo("396000.00");
        }

        @Test
        @DisplayName("VAT 미적용 항목 - VAT 0원")
        void noVat() {
            // given: 단가 50,000 * 수량 1, 할인 없음, VAT 미적용
            QuoteItem item = buildItem("50000", "30000", "1", "0", false);
            Quote quote = mockQuote();

            // when
            calculationService.calculate(quote, List.of(item));

            // then
            assertThat(item.getVatAmount()).isEqualByComparingTo("0");
            assertThat(item.getLineTotal()).isEqualByComparingTo("50000.00");
        }

        @Test
        @DisplayName("소수 수량 계산")
        void decimalQuantity() {
            // given: 단가 10,000 * 수량 2.5 = 25,000
            QuoteItem item = buildItem("10000", "6000", "2.5", "0", true);
            Quote quote = mockQuote();

            // when
            calculationService.calculate(quote, List.of(item));

            // then
            assertThat(item.getLineSupplyAmount()).isEqualByComparingTo("25000.00");
            assertThat(item.getVatAmount()).isEqualByComparingTo("2500.00");
        }
    }

    // ── 견적 합계 계산 ────────────────────────────────

    @Nested
    @DisplayName("견적 전체 합계 및 이익률 계산")
    class QuoteTotalCalculation {

        @Test
        @DisplayName("복수 항목 합계 계산")
        void multipleItems() {
            // given
            // 항목1: 100,000 * 1, 할인 0, VAT O → 공급가 100,000 / VAT 10,000
            // 항목2: 200,000 * 2, 할인 5%, VAT O → 기준 400,000 / 할인 20,000 / 공급가 380,000 / VAT 38,000
            // 합계: subtotal 500,000 / 할인 20,000 / 공급가 480,000 / VAT 48,000 / 총액 528,000
            QuoteItem item1 = buildItem("100000", "60000", "1", "0", true);
            QuoteItem item2 = buildItem("200000", "150000", "2", "5", true);
            Quote quote = mockQuote();

            // when
            calculationService.calculate(quote, List.of(item1, item2));

            // then: updateCalculation이 올바른 값으로 호출됐는지 검증
            verify(quote).updateCalculation(
                    argThat(v -> v.compareTo(new BigDecimal("500000.00")) == 0), // subtotal
                    argThat(v -> v.compareTo(new BigDecimal("20000.00")) == 0),  // discountAmount
                    argThat(v -> v.compareTo(new BigDecimal("480000.00")) == 0), // supplyAmount
                    argThat(v -> v.compareTo(new BigDecimal("48000.00")) == 0),  // taxAmount
                    argThat(v -> v.compareTo(new BigDecimal("528000.00")) == 0), // totalAmount
                    any(), any(), any()
            );
        }

        @Test
        @DisplayName("이익률 계산 - 공급가 480,000 / 원가 360,000 → 이익률 25%")
        void profitRateCalculation() {
            // given: 원가율 75% → 이익률 25%
            // 항목: 단가 200,000 * 수량 2, 할인 5%, 원가 150,000
            // 공급가 380,000 / 원가 300,000 → 이익금 80,000 → 이익률 ≈ 21.05%
            QuoteItem item = buildItem("200000", "150000", "2", "5", true);
            Quote quote = mockQuote();

            // when
            calculationService.calculate(quote, List.of(item));

            // then: 이익률 = (380,000 - 300,000) / 380,000 * 100 ≈ 21.05%
            verify(quote).updateCalculation(
                    any(), any(), any(), any(), any(),
                    argThat(v -> v.compareTo(new BigDecimal("300000.00")) == 0), // totalCost
                    argThat(v -> v.compareTo(new BigDecimal("80000.00")) == 0),  // expectedProfit
                    argThat(v -> v.compareTo(new BigDecimal("21.05")) == 0)      // profitRate
            );
        }

        @Test
        @DisplayName("공급가액 0원이면 이익률 0 반환 (역마진/ZeroDivision 방지)")
        void zeroProfitRate_whenSupplyAmountIsZero() {
            // given: 단가 0 → 공급가 0 → 이익률 계산 시 0 반환해야 함
            QuoteItem item = buildItem("0", "0", "1", "0", true);
            Quote quote = mockQuote();

            // when & then: 예외 없이 정상 동작
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> calculationService.calculate(quote, List.of(item))
            );
        }
    }
}
