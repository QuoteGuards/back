package com.project.back.domain.quote.dto.response;

import com.project.back.domain.quote.entity.QuoteItem;

import java.math.BigDecimal;

public record QuoteItemResponse(
        Long id,
        Long productId,
        String productName,
        String productCode,
        String spec, //추가
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal discountRate,
        BigDecimal discountAmount,
        boolean vatApplicable,
        BigDecimal vatAmount,
        BigDecimal lineSupplyAmount,
        BigDecimal lineTotal,
        int sortOrder,
        String discountReason
) {
    public static QuoteItemResponse from(QuoteItem item) {
        return new QuoteItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getProductCode(),
                item.getSpec(), //추가
                item.getUnitPrice(),
                item.getQuantity(),
                item.getDiscountRate(),
                item.getDiscountAmount(),
                item.getVatApplicable(),
                item.getVatAmount(),
                item.getLineSupplyAmount(),
                item.getLineTotal(),
                item.getSortOrder(),
                item.getDiscountReason()
        );
    }
}