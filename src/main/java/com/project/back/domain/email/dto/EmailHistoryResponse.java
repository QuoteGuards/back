package com.project.back.domain.email.dto;

import com.project.back.domain.email.entity.EmailSend;
import com.project.back.domain.email.entity.EmailSendStatus;
import com.project.back.domain.quote.entity.Quote;

import java.time.LocalDateTime;

public record EmailHistoryResponse(
        Long id,
        LocalDateTime sentAt,
        String quoteId,
        String buyer,
        String to,
        String subject,
        String status,
        String failureReason
) {
    public static EmailHistoryResponse from(EmailSend e) {
        Quote quote = e.getQuote();
        String buyer = (quote.getQuoteCustomer() != null)
                ? quote.getQuoteCustomer().getCompanyName() : null;

        // 성공 시 발송 일시, 그 외에는 요청 일시 노출
        LocalDateTime occurredAt = (e.getSentAt() != null) ? e.getSentAt() : e.getCreatedAt();

        return new EmailHistoryResponse(
                e.getId(),
                occurredAt,
                quote.getQuoteNumber(),
                buyer,
                e.getToEmail(),
                e.getSubject(),
                e.getStatus() == EmailSendStatus.SENT ? "성공" : "실패",
                e.getFailureReason()
        );
    }
}
