package com.project.back.domain.approval.entity;

import com.project.back.domain.quote.entity.Quote;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "quote_approval_reasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuoteApprovalReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quote Entity 생성 전 임시 처리 → 나중에 @ManyToOne으로 교체 -> 교체완료
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReasonType reasonType;

    @Column(length = 500)
    private String reasonMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum ReasonType {
        DISCOUNT_EXCEEDED,
        LOW_PROFIT,
        HIGH_AMOUNT
    }

    public static QuoteApprovalReason of(Quote quote, ReasonType reasonType, String message) {
        return QuoteApprovalReason.builder()
                .quote(quote)
                .reasonType(reasonType)
                .reasonMessage(message)
                .build();
    }
}