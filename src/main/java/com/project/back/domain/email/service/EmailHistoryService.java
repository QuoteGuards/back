package com.project.back.domain.email.service;

import com.project.back.domain.email.dto.EmailHistoryResponse;
import com.project.back.domain.email.dto.QuoteEmailRequest;
import com.project.back.domain.email.entity.EmailSend;
import com.project.back.domain.email.entity.EmailSendStatus;
import com.project.back.domain.email.repository.EmailSendRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailHistoryService {

    private final EmailSendRepository emailSendRepository;

    @Transactional(readOnly = true)
    public List<EmailHistoryResponse> getMyHistory(Long userId) {
        return emailSendRepository.findBySentByIdWithQuote(userId)
                .stream()
                .map(EmailHistoryResponse::from)
                .toList();
    }

    // 발송 결과와 독립적으로 이력을 남기기 위해 별도 트랜잭션으로 커밋
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            User sentBy,
            Quote quote,
            QuoteEmailRequest request,
            EmailSendStatus status,
            String failureReason
    ) {

        EmailSend emailSend = EmailSend.builder()
                .quote(quote)
                .sentBy(sentBy)
                .toEmail(request.to())
                .subject(request.subject())
                .body(request.body())
                .status(status)
                .failureReason(truncate(failureReason))
                .sentAt(status == EmailSendStatus.SENT ? LocalDateTime.now() : null)
                .build();

        emailSendRepository.save(emailSend);
    }

    private String truncate(String message) {

        if (message == null) {
            return null;
        }

        return message.length() > 500
                ? message.substring(0, 500)
                : message;
    }
}
