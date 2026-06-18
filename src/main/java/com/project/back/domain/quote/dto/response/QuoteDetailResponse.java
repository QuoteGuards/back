package com.project.back.domain.quote.dto.response;

import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.dto.response.QuoteItemResponse;
import com.project.back.domain.quote.entity.Quote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record QuoteDetailResponse(

        // 기본 정보
        Long id,
        String quoteNumber,
        QuoteStatus status,
        int versionNo,
        boolean approvalRequired,
        List<ApprovalReasonType> approvalReasons,

        //고객 정보
        Long customerId,
        String companyName,
        String contactName,
        String email,
        String phone,
        String address,

        //제품 항목
        List<QuoteItemResponse> items,

        //발행일 / 유효기간
        LocalDate validUntil,
        LocalDateTime createdAt,

        //상담 메모
        String internalMemo,

        //금액 계산 (공급가액, 할인, VAT, 최종)
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal supplyAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {
    public static QuoteDetailResponse from(Quote quote) {
        List<ApprovalReasonType> reasons = quote.getApprovalReasons().stream()
                .map(r -> r.getReasonType())
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
                quote.getCustomer().getCompanyName(),
                quote.getCustomer().getContactName(),
                quote.getCustomer().getEmail(),
                quote.getCustomer().getPhone(),
                quote.getCustomer().getAddress(),
                itemResponses,
                quote.getValidUntil(),
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
