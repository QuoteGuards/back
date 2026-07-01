package com.project.back.domain.email.service;

import com.project.back.domain.document.dto.QuotePdfData;
import com.project.back.domain.document.service.QuoteDocumentService;
import com.project.back.domain.email.dto.QuoteEmailRequest;
import com.project.back.domain.email.entity.EmailSendStatus;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.quote.service.QuoteService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.notification.entity.NotificationRelatedType;
import com.project.back.notification.entity.NotificationType;
import com.project.back.notification.service.NotificationService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteEmailService {

    private final JavaMailSender mailSender;
    private final QuoteRepository quoteRepository;
    private final QuoteService quoteService;
    private final UserRepository userRepository;
    private final QuoteDocumentService documentService;
    private final EmailHistoryService emailHistoryService;
    private final NotificationService notificationService;

    @Value("${mail.from-address}")
    private String fromAddress;

    @Value("${mail.from-name:QuoteGuard}")
    private String fromName;

    @Transactional
    public void sendQuoteEmail(
            String quoteNumber,
            Long userId,
            QuoteEmailRequest request
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Quote quote = quoteRepository.findByQuoteNumberAndCreatedBy(quoteNumber, user)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));

        quoteService.validateQuoteSendable(quote);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, request.attachPdf(), StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress, fromName);
            helper.setTo(request.to());

            if (StringUtils.hasText(request.cc())) {
                helper.setCc(request.cc());
            }

            helper.setSubject(request.subject());
            helper.setText(request.body() != null ? request.body() : "", false);

            if (request.attachPdf()) {
                QuotePdfData.DocumentResult pdf = documentService.generatePdf(quote);
                helper.addAttachment(pdf.fileName(), new ByteArrayResource(pdf.content()));
            }

            mailSender.send(message);

        } catch (MessagingException | IOException | RuntimeException e) {
            log.error("견적서 이메일 발송 실패 - quoteNumber={}, to={}", quoteNumber, maskEmail(request.to()), e);
            emailHistoryService.record(user, quote, request, EmailSendStatus.FAILED, e.getMessage());
            notificationService.create(
                    userId,
                    NotificationType.EMAIL_FAILED,
                    "견적서 발송 실패",
                    "견적서 " + quoteNumber + " 이메일 발송에 실패했습니다.",
                    NotificationRelatedType.QUOTE,
                    quote.getId());
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }

        // 발송 성공 후 이력 저장 - 이력 저장 실패가 발송 결과를 FAILED로 뒤집지 않도록 try 밖에서 처리
        emailHistoryService.record(user, quote, request, EmailSendStatus.SENT, null);
        quoteService.markQuoteAsSent(quote);
        notificationService.create(
                userId,
                NotificationType.EMAIL_SENT,
                "견적서 발송 완료",
                "견적서 " + quoteNumber + " 이메일을 발송했습니다.",
                NotificationRelatedType.QUOTE,
                quote.getId());
    }

    // 로그에 수신자 이메일을 평문 노출하지 않도록 로컬 파트 앞 2자만 남기고 마스킹
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2 ? local : local.substring(0, 2);
        return visible + "***" + domain;
    }
}
