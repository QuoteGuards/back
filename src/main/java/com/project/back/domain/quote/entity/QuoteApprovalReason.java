package com.project.back.domain.quote.entity;

import com.project.back.global.enums.ApprovalReasonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "quote_approval_reasons",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quote_approval_reasons_quote_reason",
                columnNames = {"quote_id", "reason_type"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuoteApprovalReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 30)
    private ApprovalReasonType reasonType;

    @Column(name = "reason_message", length = 500)
    private String reasonMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
