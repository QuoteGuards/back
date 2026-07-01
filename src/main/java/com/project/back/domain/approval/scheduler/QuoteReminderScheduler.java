package com.project.back.domain.approval.scheduler;

import com.project.back.domain.approval.entity.QuoteReminderLog.TriggerType;
import com.project.back.domain.approval.service.QuoteReminderEmailService;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteReminderScheduler {

    private final QuoteRepository quoteRepository;
    private final QuoteReminderEmailService quoteReminderEmailService;

    // 매일 오전 9시 실행
    @Scheduled(cron = "0 0 9 * * *")
    public void sendQuoteReminders() {
        LocalDateTime now = LocalDateTime.now();

        sendReminders(now.minusDays(7), TriggerType.WEEK);
        sendReminders(now.minusDays(30), TriggerType.MONTH);
    }

    private void sendReminders(LocalDateTime createdBefore, TriggerType triggerType) {
        List<Quote> quotes = quoteRepository.findDraftQuotesCreatedBefore(createdBefore);
        log.info("리마인더({}) 대상 견적 {}건 처리 시작", triggerType, quotes.size());

        for (Quote quote : quotes) {
            try {
                quoteReminderEmailService.sendReminderIfNeeded(quote, triggerType);
            } catch (Exception e) {
                log.error("견적 {} 리마인더({}) 처리 중 예외: {}", quote.getId(), triggerType, e.getMessage());
            }
        }
    }
}
