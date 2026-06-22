package com.project.back.domain.email.service;

import com.project.back.domain.document.dto.QuotePdfData;
import com.project.back.domain.document.service.QuoteDocumentService;
import com.project.back.domain.email.dto.QuoteEmailRequest;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
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
@Transactional(readOnly = true)
public class QuoteEmailService {

    private final JavaMailSender mailSender;
    private final QuoteRepository quoteRepository;
    private final UserRepository userRepository;
    private final QuoteDocumentService documentService;

    @Value("${mail.from-address}")
    private String fromAddress;

    @Value("${mail.from-name:QuoteGuard}")
    private String fromName;

    public void sendQuoteEmail(String quoteNumber, Long userId, QuoteEmailRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Quote quote = quoteRepository.findByQuoteNumberAndCreatedBy(quoteNumber, user)
                .orElseThrow(() -> new CustomException(ErrorCode.QUOTE_NOT_FOUND));

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
        } catch (MessagingException | IOException e) {
            log.error("견적서 이메일 발송 실패 - quoteNumber={}, to={}", quoteNumber, request.to(), e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
