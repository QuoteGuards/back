package com.project.back.domain.quote.dto.response;

import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record QuoteListResponse(
        Long id,
        String quoteNumber,
        String customerName,
        String contactName,
        BigDecimal totalAmount,
        QuoteStatus status,
        boolean approvalRequired,
        LocalDate validUntil,
        int versionNo,
        LocalDateTime createdAt
) {
    public static QuoteListResponse from(Quote quote) {
        return new QuoteListResponse(
                quote.getId(),
                quote.getQuoteNumber(),
                quote.getCustomer().getCompanyName(),
                quote.getCustomer().getContactName(),
                quote.getTotalAmount(),
                quote.getStatus(),
                quote.getApprovalRequired(),
                quote.getValidUntil(),
                quote.getVersionNo(),
                quote.getCreatedAt()
        );
    }
}
