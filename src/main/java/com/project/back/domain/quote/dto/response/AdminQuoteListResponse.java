package com.project.back.domain.quote.dto.response;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteCustomer;
import com.project.back.domain.user.entity.User;
import com.project.back.global.enums.QuoteStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 최고관리자용 전체 견적 목록
public record AdminQuoteListResponse(
        Long id,
        String quoteNumber,
        String customerName,
        String contactName,
        Long writerId,
        String writerName,
        String writerDepartment,
        BigDecimal totalAmount,
        BigDecimal profitRate,
        BigDecimal discountRate,
        QuoteStatus status,
        boolean approvalRequired,
        LocalDate issuedDate,
        LocalDate validUntil,
        LocalDateTime createdAt
) {
    public static AdminQuoteListResponse from(Quote quote) {
        QuoteCustomer qc = quote.getQuoteCustomer();
        User writer = quote.getCreatedBy();

        BigDecimal discountRate = BigDecimal.ZERO;
        if (quote.getSubtotal() != null && quote.getSubtotal().compareTo(BigDecimal.ZERO) > 0) {
            discountRate = quote.getDiscountAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(quote.getSubtotal(), 2, RoundingMode.HALF_UP);
        }

        return new AdminQuoteListResponse(
                quote.getId(),
                quote.getQuoteNumber(),
                qc != null ? qc.getCompanyName() : null,
                qc != null ? qc.getContactName() : null,
                writer != null ? writer.getId() : null,
                writer != null ? writer.getName() : null,
                writer != null ? writer.getDepartment() : null,
                quote.getTotalAmount(),
                quote.getProfitRate(),
                discountRate,
                quote.getStatus(),
                quote.getApprovalRequired(),
                quote.getIssuedDate(),
                quote.getValidUntil(),
                quote.getCreatedAt()
        );
    }
}
