package com.project.back.domain.approval.entity;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "approval_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quote Entity 생성 전 임시 처리 → 나중에 @ManyToOne으로 교체
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(columnDefinition = "TEXT")
    private String requestMemo;

    @Column(columnDefinition = "TEXT")
    private String rejectReason;

    @Column(columnDefinition = "TEXT")
    private String aiRiskSummary;

    @Column(nullable = false)
    @Builder.Default
    private int requestCount = 1;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    @OneToMany(mappedBy = "approvalRequest", cascade = CascadeType.ALL)
    @Builder.Default
    private List<QuoteApprovalHistory> histories = new ArrayList<>();

    // ── 상태 변경 메서드 ──
    public void approve(User approver, String memo) {
        this.approver = approver;
        this.status = ApprovalStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
    }

    public void reject(User approver, String rejectReason) {
        this.approver = approver;
        this.rejectReason = rejectReason;
        this.status = ApprovalStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
    }

    public void reRequest(String requestMemo) {
        this.status = ApprovalStatus.PENDING;
        this.requestMemo = requestMemo;
        this.requestCount++;
        this.processedAt = null;
    }

    public void cancel() {
        this.status = ApprovalStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }

    public void updateMemo(String memo) {
        this.requestMemo = memo;
    }

    @PrePersist
    public void prePersist() {
        this.requestedAt = LocalDateTime.now();
        this.status = ApprovalStatus.PENDING;
    }

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}