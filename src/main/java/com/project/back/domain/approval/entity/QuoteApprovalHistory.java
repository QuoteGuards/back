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
        return QuoteApprovalHistory.builder()
                .approvalRequest(approvalRequest)
                .actor(actor)
                .action(action)
                .beforeStatus(beforeStatus)
                .afterStatus(afterStatus)
                .memo(memo)
                .build();
    }
}