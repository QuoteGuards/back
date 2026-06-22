package com.project.back.domain.email.entity;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_sends")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EmailSend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by", nullable = false)
    private User sentBy;

    @Column(name = "to_email", nullable = false, length = 255)
    private String toEmail;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EmailSendStatus status = EmailSendStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // 발송 성공 일시 (실패 시 null)
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // 발송 요청 일시
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
