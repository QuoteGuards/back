package com.project.back.domain.approval.service;

import com.project.back.domain.approval.entity.QuoteReminderLog;
import com.project.back.domain.approval.entity.QuoteReminderLog.TriggerType;
import com.project.back.domain.approval.repository.QuoteReminderLogRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.global.client.GeminiClient;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteReminderEmailService {

    private final JavaMailSender mailSender;
    private final GeminiClient geminiClient;
    private final QuoteItemRepository quoteItemRepository;
    private final QuoteReminderLogRepository quoteReminderLogRepository;

    @Value("${mail.from-address}")
    private String fromAddress;

    @Value("${mail.from-name:QuoteGuard}")
    private String fromName;

    @Transactional
    public boolean sendReminderIfNeeded(Quote quote, TriggerType triggerType) {
        String customerEmail = resolveCustomerEmail(quote);
        if (customerEmail == null || customerEmail.isBlank()) {
            log.warn("견적 {} 고객 이메일 없음, 리마인더 발송 건너뜀", quote.getId());
            return false;
        }

        if (quoteReminderLogRepository.existsByQuoteIdAndTriggerType(quote.getId(), triggerType)) {
            return false; // 이미 발송한 이력이 있으면 중복 발송 방지
        }

        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quote.getId());
        String staffName = quote.getCreatedBy().getName();
        String triggerLabel = triggerType == TriggerType.WEEK ? "일주일" : "한 달";

        String emailBody = generateEmailBody(staffName, triggerLabel, quote, items);

        try {
            sendEmail(customerEmail, staffName, emailBody);
            quoteReminderLogRepository.save(
                    QuoteReminderLog.of(quote, triggerType, customerEmail)
            );
            log.info("견적 {} 리마인더({}) 발송 완료 → {}", quote.getId(), triggerType, customerEmail);
            return true;
        } catch (Exception e) {
            log.error("견적 {} 리마인더 발송 실패: {}", quote.getId(), e.getMessage());
            return false;
        }
    }

    private String generateEmailBody(String staffName, String triggerLabel,
                                     Quote quote, List<QuoteItem> items) {
        String prompt = buildGeminiPrompt(staffName, triggerLabel, quote, items);
        try {
            return geminiClient.generateContent(prompt);
        } catch (Exception e) {
            log.warn("Gemini 이메일 문구 생성 실패, 기본 템플릿 사용: {}", e.getMessage());
            return buildFallbackBody(staffName, triggerLabel, quote, items);
        }
    }

    private String buildGeminiPrompt(String staffName, String triggerLabel,
                                     Quote quote, List<QuoteItem> items) {
        NumberFormat fmt = NumberFormat.getInstance(Locale.KOREA);
        StringBuilder sb = new StringBuilder();

        sb.append("당신은 B2B 영업 이메일 전문 작성가입니다.\n");
        sb.append("아래 정보를 바탕으로 고객에게 보내는 견적 리마인더 이메일 본문을 작성해 주세요.\n");
        sb.append("- 한국어로 작성\n");
        sb.append("- 친근하고 정중한 톤\n");
        sb.append("- HTML 태그 없이 순수 텍스트로만 작성\n");
        sb.append("- 아래 형식을 반드시 따를 것:\n\n");

        sb.append("[형식]\n");
        sb.append("안녕하세요 큐레이터 ").append(staffName).append("입니다.\n");
        sb.append(triggerLabel).append(" 전에 구매 희망하신 견적이 있어 연락 드립니다.\n");
        sb.append("(견적 내용 요약 1~2문장)\n\n");
        sb.append("[견적 내용]\n");
        sb.append("(품목 목록)\n");
        sb.append("합계: ").append(fmt.format(quote.getTotalAmount())).append("원\n\n");
        sb.append("다른 회사보다 저희 회사에서 더 좋은 견적을 확인해드릴 수 있습니다.\n");
        sb.append("항상 최고가 되는 QUOTEGUARD가 되겠습니다!\n\n");

        sb.append("[견적 정보]\n");
        sb.append("- 영업사원: ").append(staffName).append("\n");
        sb.append("- 견적 번호: ").append(quote.getQuoteNumber()).append("\n");
        sb.append("- 총 금액: ").append(fmt.format(quote.getTotalAmount())).append("원\n");

        if (!items.isEmpty()) {
            sb.append("- 품목:\n");
            for (QuoteItem item : items) {
                sb.append("  · ").append(item.getProductName())
                        .append("  단가 ").append(fmt.format(item.getUnitPrice())).append("원")
                        .append(" × ").append(item.getQuantity()).append("개\n");
            }
        }

        return sb.toString();
    }

    private String buildFallbackBody(String staffName, String triggerLabel,
                                     Quote quote, List<QuoteItem> items) {
        NumberFormat fmt = NumberFormat.getInstance(Locale.KOREA);
        StringBuilder sb = new StringBuilder();

        sb.append("안녕하세요 큐레이터 ").append(staffName).append("입니다.\n\n");
        sb.append(triggerLabel).append(" 전에 구매 희망하신 견적이 있어 연락 드립니다.\n\n");

        sb.append("[견적 내용]\n");
        for (QuoteItem item : items) {
            sb.append("- ").append(item.getProductName())
                    .append("  단가 ").append(fmt.format(item.getUnitPrice())).append("원")
                    .append(" × ").append(item.getQuantity()).append("개\n");
        }
        sb.append("합계: ").append(fmt.format(quote.getTotalAmount())).append("원\n\n");

        sb.append("다른 회사보다 저희 회사에서 더 좋은 견적을 확인해드릴 수 있습니다.\n");
        sb.append("항상 최고가 되는 QUOTEGUARD가 되겠습니다!");

        return sb.toString();
    }

    private void sendEmail(String to, String staffName, String body)
            throws MessagingException, java.io.UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

        helper.setFrom(fromAddress, fromName);
        helper.setTo(to);
        helper.setSubject("[QUOTEGUARD] 큐레이터 " + staffName + "님의 견적 안내");
        helper.setText(body, false);

        mailSender.send(message);
    }

    private String resolveCustomerEmail(Quote quote) {
        // QuoteCustomer 스냅샷 우선, 없으면 Customer 엔티티에서
        if (quote.getQuoteCustomer() != null
                && quote.getQuoteCustomer().getEmail() != null
                && !quote.getQuoteCustomer().getEmail().isBlank()) {
            return quote.getQuoteCustomer().getEmail();
        }
        if (quote.getCustomer() != null) {
            return quote.getCustomer().getEmail();
        }
        return null;
    }
}
