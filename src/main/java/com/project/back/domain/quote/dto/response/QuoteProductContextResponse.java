package com.project.back.domain.quote.dto.response;

import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.domain.product.entity.Product;

import java.math.BigDecimal;

// 견적 작성용 — catalog API와 분리 (원가·할인정책 포함)
public record QuoteProductContextResponse(
        Long productId,
        String productName,
        String productCode,
        String spec,
        BigDecimal unitPrice,
        BigDecimal costPrice,
        boolean vatApplicable,
        Long discountPolicyId,
        BigDecimal maxDiscountRate,
        BigDecimal minProfitRate
) {
    public static QuoteProductContextResponse of(Product product, DiscountPolicy policy) {
        return new QuoteProductContextResponse(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getSpec(),
                product.getUnitPrice(),
                product.getCostPrice(),
                product.isVatApplicable(),
                policy != null ? policy.getId() : null,
                policy != null ? policy.getMaxDiscountRate() : null,
                policy != null ? policy.getMinProfitRate() : null
        );
    }
}
