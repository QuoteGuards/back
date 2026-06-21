package com.project.back.domain.quote.service;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


@Service
public class QuoteCalculationService {

    private static final BigDecimal VAT_RATE = new BigDecimal("0.1");
    private static final int SCALE = 2;

    // 항목별 금액 계산 후 Quote 전체 합계 업데이트
    public void calculate(Quote quote, List<QuoteItem> items) {
        // 1st: 항목별 계산
        items.forEach(this::calculateItem);

        // 2nd: 견적 합계 계산
        BigDecimal subtotal         = BigDecimal.ZERO;
        BigDecimal totalDiscount    = BigDecimal.ZERO;
        BigDecimal totalSupply      = BigDecimal.ZERO;
        BigDecimal totalTax         = BigDecimal.ZERO;
        BigDecimal totalCost        = BigDecimal.ZERO;

        for (QuoteItem item : items) {
            BigDecimal itemBase = item.getUnitPrice().multiply(item.getQuantity());
            subtotal      = subtotal.add(itemBase);
            totalDiscount = totalDiscount.add(item.getDiscountAmount());
            totalSupply   = totalSupply.add(item.getLineSupplyAmount());
            totalTax      = totalTax.add(item.getVatAmount());
            totalCost     = totalCost.add(item.getCostPrice().multiply(item.getQuantity()));
        }

        BigDecimal totalAmount          = totalSupply.add(totalTax);
        BigDecimal expectedProfit       = totalSupply.subtract(totalCost);
        BigDecimal profitRate           = calcProfitRate(expectedProfit, totalSupply);

        // 3rd: Quote 엔티티에 결과 반영
        quote.updateCalculation(
                subtotal.setScale(SCALE, RoundingMode.HALF_UP),
                totalDiscount.setScale(SCALE, RoundingMode.HALF_UP),
                totalSupply.setScale(SCALE, RoundingMode.HALF_UP),
                totalTax.setScale(SCALE, RoundingMode.HALF_UP),
                totalAmount.setScale(SCALE, RoundingMode.HALF_UP),
                totalCost.setScale(SCALE, RoundingMode.HALF_UP),
                expectedProfit.setScale(SCALE, RoundingMode.HALF_UP),
                profitRate.setScale(SCALE, RoundingMode.HALF_UP)
        );
    }

    //항목별 계산
    private void calculateItem(QuoteItem item) {
        BigDecimal base           = item.getUnitPrice().multiply(item.getQuantity());
        BigDecimal discountAmount = calcDiscountAmount(base, item.getDiscountRate());
        BigDecimal lineSupply     = base.subtract(discountAmount);
        BigDecimal vatAmount      = item.getVatApplicable()
                ? lineSupply.multiply(VAT_RATE).setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal lineTotal      = lineSupply.add(vatAmount);

        item.updateCalculation(
                discountAmount.setScale(SCALE, RoundingMode.HALF_UP),
                lineSupply.setScale(SCALE, RoundingMode.HALF_UP),
                vatAmount,
                lineTotal.setScale(SCALE, RoundingMode.HALF_UP)
        );
    }

    // 할인금액 = 기준금액 * (할인율 / 100)
    private BigDecimal calcDiscountAmount(BigDecimal base, BigDecimal discountRate) {
        if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return base.multiply(discountRate)
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
    }

    // 이익률 = (예상 이익금 / 공급가액) * 100 [공급가액이 0이면 0 반환 (역마진 등 예외 방지)]
    private BigDecimal calcProfitRate(BigDecimal expectedProfit, BigDecimal supplyAmount) {
        if (supplyAmount == null || supplyAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return expectedProfit
                .multiply(BigDecimal.valueOf(100))
                .divide(supplyAmount, SCALE, RoundingMode.HALF_UP);
    }
}
