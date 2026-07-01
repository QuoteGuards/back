package com.project.back.domain.quote.dto.response;

import com.project.back.domain.approval.entity.QuoteApprovalReason;
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

        Long discountPolicyId,
        BigDecimal maxDiscountRate,
        BigDecimal minProfitRate,

        //고객 정보 (스냅샷 기반 조회)
        Long customerId,
        String companyName,
        String contactName,
        String email,
        String phone,
        String address,

        //자사(발행처) 정보 스냅샷 - PDF/문서 발행용
        CompanyInfo company,

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
    public record CompanyInfo(
            String name,
            String address,
            String phone,
            String email,
            String businessNumber
    ) {
        public static CompanyInfo from(com.project.back.domain.quote.entity.QuoteCompany company) {
            if (company == null) return null;
            return new CompanyInfo(
                    company.getName(),
                    company.getAddress(),
                    company.getPhone(),
                    company.getEmail(),
                    company.getBusinessNumber()
            );
        }
    }

    public static QuoteDetailResponse from(Quote quote) {
        List<ApprovalReasonType> reasons = quote.getApprovalReasons().stream()
                .map(QuoteApprovalReason::getReasonType)
                .toList();

        List<QuoteItemResponse> itemResponses = quote.getItems().stream()
                .map(QuoteItemResponse::from)
                .toList();

        var policy = quote.getDiscountPolicy();

        return new QuoteDetailResponse(
                quote.getId(),
                quote.getQuoteNumber(),
                quote.getStatus(),
                quote.getVersionNo(),
                quote.getApprovalRequired(),
                reasons,
                policy != null ? policy.getId() : null,
                policy != null ? policy.getMaxDiscountRate() : null,
                policy != null ? policy.getMinProfitRate() : null,
                quote.getCustomer().getId(),
                quote.getQuoteCustomer().getCompanyName(), // 추가
                quote.getQuoteCustomer().getContactName(), // 추가
                quote.getQuoteCustomer().getEmail(),       // 추가
                quote.getQuoteCustomer().getPhone(),       // 추가
                quote.getQuoteCustomer().getAddress(),     // 추가
                CompanyInfo.from(quote.getCompany()),      // 자사 스냅샷 추가
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