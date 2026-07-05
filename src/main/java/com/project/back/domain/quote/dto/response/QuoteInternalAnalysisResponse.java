package com.project.back.domain.quote.dto.response;

import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record QuoteInternalAnalysisResponse(

        Long quoteId,
        String quoteNumber,

        // 고객용 금액 요약
        BigDecimal supplyAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,

        // 내부 검토 핵심 지표
        BigDecimal totalCostAmount,
        BigDecimal expectedProfitAmount,
        BigDecimal profitRate,

        // 승인 필요 여부 및 사유
        boolean approvalRequired,
        List<ApprovalReasonType> approvalReasons,

        /** 견적 전체 LOW_PROFIT 판단·UI 색상용 — 품목 policy minProfitRate 중 최대(strictest) */
        BigDecimal strictestMinProfitRate,

        List<QuoteItemInternalResponse> items
) {
    public static QuoteInternalAnalysisResponse from(Quote quote) {
        List<ApprovalReasonType> reasons = quote.getApprovalReasons().stream()
                .map(QuoteApprovalReason::getReasonType)
                .toList();

        List<QuoteItemInternalResponse> itemDetails = quote.getItems().stream()
                .map(QuoteItemInternalResponse::from)
                .toList();

        return new QuoteInternalAnalysisResponse(
                quote.getId(),
                quote.getQuoteNumber(),
                quote.getSupplyAmount(),
                quote.getTaxAmount(),
                quote.getTotalAmount(),
                quote.getTotalCostAmount(),
                quote.getExpectedProfitAmount(),
                quote.getProfitRate(),
                quote.getApprovalRequired(),
                reasons,
                resolveStrictestMinProfitRate(quote.getItems(), quote),
                itemDetails
        );
    }

    public static QuoteInternalAnalysisResponse from(
            Quote quote,
            List<QuoteItem> items,
            boolean approvalRequired,
            List<ApprovalReasonType> approvalReasons
    ) {
        List<QuoteItemInternalResponse> itemDetails = items.stream()
                .map(QuoteItemInternalResponse::from)
                .toList();

        return new QuoteInternalAnalysisResponse(
                quote.getId(),
                quote.getQuoteNumber(),
                quote.getSupplyAmount(),
                quote.getTaxAmount(),
                quote.getTotalAmount(),
                quote.getTotalCostAmount(),
                quote.getExpectedProfitAmount(),
                quote.getProfitRate(),
                approvalRequired,
                approvalReasons,
                resolveStrictestMinProfitRate(items, quote),
                itemDetails
        );
    }

    private static BigDecimal resolveStrictestMinProfitRate(List<QuoteItem> items, Quote quote) {
        BigDecimal fromItems = resolveStrictestMinProfitRate(items);
        if (fromItems != null) {
            return fromItems;
        }
        var headerPolicy = quote.getDiscountPolicy();
        return headerPolicy != null ? headerPolicy.getMinProfitRate() : null;
    }

    private static BigDecimal resolveStrictestMinProfitRate(List<QuoteItem> items) {
        return items.stream()
                .map(QuoteItem::getDiscountPolicy)
                .filter(Objects::nonNull)
                .map(DiscountPolicy::getMinProfitRate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    public record QuoteItemInternalResponse(
            Long id,
            String productName,
            BigDecimal unitPrice,
            BigDecimal costPrice,
            BigDecimal quantity,
            BigDecimal discountRate,
            BigDecimal lineSupplyAmount,
            BigDecimal lineTotal,
            String discountReason,
            BigDecimal maxDiscountRate,
            BigDecimal minProfitRate
    ) {
        public static QuoteItemInternalResponse from(QuoteItem item) {
            DiscountPolicy policy = item.getDiscountPolicy();
            return new QuoteItemInternalResponse(
                    item.getId(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getCostPrice(),
                    item.getQuantity(),
                    item.getDiscountRate(),
                    item.getLineSupplyAmount(),
                    item.getLineTotal(),
                    item.getDiscountReason(),
                    policy != null ? policy.getMaxDiscountRate() : null,
                    policy != null ? policy.getMinProfitRate() : null
            );
        }
    }
}
