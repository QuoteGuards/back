package com.project.back.domain.quote.dto.response;

import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record QuoteDetailResponse(
        Long id,
        String quoteNumber,
        QuoteStatus status,
        int versionNo,
        boolean approvalRequired,
        List<ApprovalReasonType> approvalReasons,

        //고객 정보 (스냅샷 기반 조회)
        Long customerId,
        String companyName,
        String contactName,
        String email,
        String phone,
        String address,

        List<QuoteItemResponse> items,
        LocalDate issuedDate,
        LocalDate validUntil,
        String deliveryTerm,
        LocalDateTime createdAt,
        String internalMemo,

        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal supplyAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {
    public static QuoteDetailResponse from(Quote quote) {
        List<ApprovalReasonType> reasons = quote.getApprovalReasons().stream()
                .map(r -> ApprovalReasonType.valueOf(r.getReasonType().name()))
                .toList();

        List<QuoteItemResponse> itemResponses = quote.getItems().stream()
                .map(QuoteItemResponse::from)
                .toList();

        return new QuoteDetailResponse(
                quote.getId(),
                quote.getQuoteNumber(),
                quote.getStatus(),
                quote.getVersionNo(),
                quote.getApprovalRequired(),
                reasons,
                quote.getCustomer().getId(),
                quote.getQuoteCustomer().getCompanyName(), // 추가
                quote.getQuoteCustomer().getContactName(), // 추가
                quote.getQuoteCustomer().getEmail(),       // 추가
                quote.getQuoteCustomer().getPhone(),       // 추가
                quote.getQuoteCustomer().getAddress(),     // 추가
                itemResponses,
                quote.getIssuedDate(),
                quote.getValidUntil(),
                quote.getDeliveryTerm(),
                quote.getCreatedAt(),
                quote.getInternalMemo(),
                quote.getSubtotal(),
                quote.getDiscountAmount(),
                quote.getSupplyAmount(),
                quote.getTaxAmount(),
                quote.getTotalAmount()
        );
    }
}