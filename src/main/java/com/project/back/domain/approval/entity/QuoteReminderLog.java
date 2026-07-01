package com.project.back.domain.approval.entity;

import com.project.back.domain.quote.entity.Quote;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "quote_reminder_log",
        uniqueConstraints = @UniqueConstraint(columnNames = {"quote_id", "trigger_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class QuoteReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 10)
    private TriggerType triggerType;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    public enum TriggerType {
        WEEK, MONTH
    }

    public static QuoteReminderLog of(Quote quote, TriggerType triggerType, String customerEmail) {
        return QuoteReminderLog.builder()
                .quote(quote)
                .triggerType(triggerType)
                .customerEmail(customerEmail)
                .build();
    }
}
