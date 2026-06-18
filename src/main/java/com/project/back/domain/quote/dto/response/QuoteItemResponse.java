package com.project.back.domain.quote.dto.response;

import com.project.back.domain.quote.entity.QuoteItem;

import java.math.BigDecimal;

public record QuoteItemResponse(
        Long id,
        Long productId,
        String productName,
        String productCode,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal discountRate,
        BigDecimal discountAmount,
        boolean vatApplicable,
        BigDecimal vatAmount,
        BigDecimal lineSupplyAmount,  // 소계 (VAT 제외)
        BigDecimal lineTotal,         // 합계 (VAT 포함)
        int sortOrder
) {
    public static QuoteItemResponse from(QuoteItem item) {
        return new QuoteItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getProductCode(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getDiscountRate(),
                item.getDiscountAmount(),
                item.getVatApplicable(),
                item.getVatAmount(),
                item.getLineSupplyAmount(),
                item.getLineTotal(),
                item.getSortOrder()
        );
    }
}
