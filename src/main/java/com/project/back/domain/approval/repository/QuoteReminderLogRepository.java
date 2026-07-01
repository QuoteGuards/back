package com.project.back.domain.approval.repository;

import com.project.back.domain.approval.entity.QuoteReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteReminderLogRepository extends JpaRepository<QuoteReminderLog, Long> {

    boolean existsByQuoteIdAndTriggerType(Long quoteId, QuoteReminderLog.TriggerType triggerType);
}
