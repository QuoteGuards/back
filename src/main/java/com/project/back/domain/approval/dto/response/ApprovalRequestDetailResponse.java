package com.project.back.domain.approval.dto.response;

import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalHistory;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ApprovalRequestDetailResponse {

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
    private int requestCount;

    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

    // 승인 필요 사유 목록
    private List<ApprovalReasonResponse> reasons;

    // 승인 이력 목록
    private List<ApprovalHistoryResponse> histories;

    public static ApprovalRequestDetailResponse from(
            ApprovalRequest entity,
            List<QuoteApprovalReason> reasons,
            List<QuoteApprovalHistory> histories
    ) {
        return ApprovalRequestDetailResponse.builder()
                .id(entity.getId())
                .quoteId(entity.getQuote().getId())
                .requesterId(entity.getRequester().getId())
                .requesterName(entity.getRequester().getName())
                .approverId(entity.getApprover() != null
                        ? entity.getApprover().getId() : null)
                .approverName(entity.getApprover() != null
                        ? entity.getApprover().getName() : null)
                .status(entity.getStatus().name())
                .requestMemo(entity.getRequestMemo())
                .rejectReason(entity.getRejectReason())
                .requestCount(entity.getRequestCount())
                .requestedAt(entity.getRequestedAt())
                .processedAt(entity.getProcessedAt())
                .reasons(reasons.stream()
                        .map(ApprovalReasonResponse::from)
                        .toList())
                .histories(histories.stream()
                        .map(ApprovalHistoryResponse::from)
                        .toList())
                .build();
    }
}