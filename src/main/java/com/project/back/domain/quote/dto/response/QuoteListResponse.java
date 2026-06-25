package com.project.back.domain.quote.dto.response;

import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteCustomer;

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
        QuoteCustomer qc = quote.getQuoteCustomer();
        return new QuoteListResponse(
                quote.getId(),
                quote.getQuoteNumber(),
                qc != null ? qc.getCompanyName() : null,
                qc != null ? qc.getContactName() : null,
                quote.getTotalAmount(),
                quote.getStatus(),
                quote.getApprovalRequired(),
                quote.getValidUntil(),
                quote.getVersionNo(),
                quote.getCreatedAt()
        );
    }
}