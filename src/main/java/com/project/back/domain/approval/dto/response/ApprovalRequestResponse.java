package com.project.back.domain.approval.dto.response;

import com.project.back.domain.approval.entity.ApprovalRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApprovalRequestResponse {

    private Long id;
    private Long quoteId;

    // 요청자 정보
    private Long requesterId;
    private String requesterName;

    // 승인자 정보 (처리 전 null)
    private Long approverId;
    private String approverName;

    private String status;
    private String requestMemo;
    private String rejectReason;
    private String approveMemo;

    private int requestCount;

    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

    // Entity → DTO 변환 메서드
    public static ApprovalRequestResponse from(ApprovalRequest entity) {
        return ApprovalRequestResponse.builder()
                .id(entity.getId())
                .quoteId(entity.getQuote().getId())
                .requesterId(entity.getRequester().getId())
                .requesterName(entity.getRequester().getName())
                .approverId(entity.getApprover() != null ? entity.getApprover().getId() : null)
                .approverName(entity.getApprover() != null ? entity.getApprover().getName() : null)
                .status(entity.getStatus().name())
                .requestMemo(entity.getRequestMemo())
                .rejectReason(entity.getRejectReason())
                .approveMemo(entity.getApproveMemo())
                .requestCount(entity.getRequestCount())
                .requestedAt(entity.getRequestedAt())
                .processedAt(entity.getProcessedAt())
                .build();
    }
}