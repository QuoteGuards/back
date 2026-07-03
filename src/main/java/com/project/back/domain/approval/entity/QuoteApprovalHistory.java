package com.project.back.domain.approval.entity;

import com.project.back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "quote_approval_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuoteApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType action;

    @Enumerated(EnumType.STRING)
    private ApprovalRequest.ApprovalStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    private ApprovalRequest.ApprovalStatus afterStatus;

    @Column(columnDefinition = "TEXT")
    private String memo;

    // 반려/재요청 시점의 견적 스냅샷(JSON). action이 REJECTED/RE_REQUESTED일 때만 채워짐
    @Column(columnDefinition = "TEXT")
    private String quoteSnapshot;

    @Column(nullable = false)
    private LocalDateTime actedAt;

    @PrePersist
    public void prePersist() {
        this.actedAt = LocalDateTime.now();
    }

    public enum ActionType {
        REQUESTED,
        APPROVED,
        REJECTED,
        RE_REQUESTED,
        CANCELLED
    }

    public static QuoteApprovalHistory of(
            ApprovalRequest approvalRequest,
            User actor,
            ActionType action,
            ApprovalRequest.ApprovalStatus beforeStatus,
            ApprovalRequest.ApprovalStatus afterStatus,
            String memo
    ) {
        return of(approvalRequest, actor, action, beforeStatus, afterStatus, memo, null);
    }

    public static QuoteApprovalHistory of(
            ApprovalRequest approvalRequest,
            User actor,
            ActionType action,
            ApprovalRequest.ApprovalStatus beforeStatus,
            ApprovalRequest.ApprovalStatus afterStatus,
            String memo,
            String quoteSnapshot
    ) {
        return QuoteApprovalHistory.builder()
                .approvalRequest(approvalRequest)
                .actor(actor)
                .action(action)
                .beforeStatus(beforeStatus)
                .afterStatus(afterStatus)
                .memo(memo)
                .quoteSnapshot(quoteSnapshot)
                .build();
    }
}