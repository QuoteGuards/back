package com.project.back.domain.quote.dto.response;

import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.domain.quote.entity.Quote;

import java.math.BigDecimal;
import java.util.List;

public record QuoteInternalAnalysisResponse(

        Long quoteId,
        String quoteNumber,

        // 고객용 금액 요약
        BigDecimal supplyAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,

        // 내부 검토 핵심 지표
        BigDecimal totalCostAmount,         // 원가 합계
        BigDecimal expectedProfitAmount,    // 예상 이익금
        BigDecimal profitRate,              // 이익률 (%)

        // 승인 필요 여부 및 사유
        boolean approvalRequired,
        List<ApprovalReasonType> approvalReasons,

        // 항목별 원가 포함 상세
        List<QuoteItemInternalResponse> items
) {
    public static QuoteInternalAnalysisResponse from(Quote quote) {
        List<ApprovalReasonType> reasons = quote.getApprovalReasons().stream()
                .map(r -> ApprovalReasonType.valueOf(r.getReasonType().name()))
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
                itemDetails
        );
    }

    //항목별 내부 검토 데이터 (원가 포함)
    public record QuoteItemInternalResponse(
            Long id,
            String productName,
            BigDecimal unitPrice,
            BigDecimal costPrice,       // 원가
            BigDecimal quantity,
            BigDecimal discountRate,
            BigDecimal lineSupplyAmount,
            BigDecimal lineTotal
    ) {
        public static QuoteItemInternalResponse from(com.project.back.domain.quote.entity.QuoteItem item) {
            return new QuoteItemInternalResponse(
                    item.getId(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getCostPrice(),
                    item.getQuantity(),
                    item.getDiscountRate(),
                    item.getLineSupplyAmount(),
                    item.getLineTotal()
            );
        }
    }
}
