package com.project.back.domain.approval.controller;

import com.project.back.domain.approval.entity.QuoteReminderLog.TriggerType;
import com.project.back.domain.approval.service.QuoteReminderEmailService;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class QuoteReminderTestController {

    private final QuoteRepository quoteRepository;
    private final QuoteReminderEmailService quoteReminderEmailService;

    /**
     * 리마인더 이메일 즉시 발송 테스트 (SUPER_ADMIN 전용)
     *
     * POST /api/admin/reminder-test?triggerType=WEEK   → 7일 이상 된 DRAFT 견적 대상
     * POST /api/admin/reminder-test?triggerType=MONTH  → 30일 이상 된 DRAFT 견적 대상
     * POST /api/admin/reminder-test?triggerType=WEEK&daysAgo=1  → daysAgo일 이상 된 견적 대상 (날짜 조건 직접 지정)
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/reminder-test")
    public ResponseEntity<String> triggerReminderTest(
            @RequestParam TriggerType triggerType,
            @RequestParam(defaultValue = "-1") int daysAgo
    ) {
        int days = daysAgo >= 0 ? daysAgo : (triggerType == TriggerType.WEEK ? 7 : 30);
        LocalDateTime createdBefore = LocalDateTime.now().minusDays(days);

        List<Quote> quotes = quoteRepository.findDraftQuotesCreatedBefore(createdBefore);

        if (quotes.isEmpty()) {
            return ResponseEntity.ok(
                    "[테스트] 조건에 맞는 DRAFT 견적 없음 (생성일 기준 " + days + "일 이상 경과)"
            );
        }

        int sentCount = 0;
        for (Quote quote : quotes) {
            if (quoteReminderEmailService.sendReminderIfNeeded(quote, triggerType)) {
                sentCount++;
            }
        }

        return ResponseEntity.ok(
                "[테스트] " + triggerType + " 리마인더 처리 완료 — 대상 견적 " + quotes.size()
                        + "건 중 " + sentCount + "건 발송 성공 (실패/중복/이메일없음 " + (quotes.size() - sentCount) + "건)"
        );
    }
}
