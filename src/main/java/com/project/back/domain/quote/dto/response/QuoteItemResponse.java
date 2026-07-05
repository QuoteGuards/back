package com.project.back.domain.quote.dto.response;

import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.domain.quote.entity.QuoteItem;

import java.math.BigDecimal;

public record QuoteItemResponse(
        Long id,
        Long productId,
        String productName,
        String productCode,
        String spec,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal discountRate,
        BigDecimal discountAmount,
        boolean vatApplicable,
        BigDecimal vatAmount,
        BigDecimal lineSupplyAmount,
        BigDecimal lineTotal,
        int sortOrder,
        String discountReason,
        Long discountPolicyId,
        BigDecimal maxDiscountRate,
        BigDecimal minProfitRate
) {
    public static QuoteItemResponse from(QuoteItem item) {
        DiscountPolicy policy = item.getDiscountPolicy();
        return new QuoteItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getProductCode(),
                item.getSpec(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getDiscountRate(),
                item.getDiscountAmount(),
                item.getVatApplicable(),
                item.getVatAmount(),
                item.getLineSupplyAmount(),
                item.getLineTotal(),
                item.getSortOrder(),
                item.getDiscountReason(),
                policy != null ? policy.getId() : null,
                policy != null ? policy.getMaxDiscountRate() : null,
                policy != null ? policy.getMinProfitRate() : null
        );
    }
}
