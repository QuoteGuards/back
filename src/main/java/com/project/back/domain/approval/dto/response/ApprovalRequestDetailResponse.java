package com.project.back.domain.approval.dto.response;

import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalHistory;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
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
    private String approveMemo;
    private int requestCount;

    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

    // 견적 금액 요약 (QuoteInternalAnalysisResponse와 동일한 값 재사용)
    private BigDecimal totalAmount;
    private BigDecimal totalCostAmount;
    private BigDecimal expectedProfitAmount;
    private BigDecimal profitRate;
    private List<ApprovalQuoteItemResponse> items;

    // 승인 필요 사유 목록
    private List<ApprovalReasonResponse> reasons;

    // 승인 이력 목록
    private List<ApprovalHistoryResponse> histories;

    public static ApprovalRequestDetailResponse from(
            ApprovalRequest entity,
            Quote quote,
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
                .approveMemo(entity.getApproveMemo())
                .requestCount(entity.getRequestCount())
                .requestedAt(entity.getRequestedAt())
                .processedAt(entity.getProcessedAt())
                .totalAmount(quote.getTotalAmount())
                .totalCostAmount(quote.getTotalCostAmount())
                .expectedProfitAmount(quote.getExpectedProfitAmount())
                .profitRate(quote.getProfitRate())
                .items(quote.getItems().stream()
                        .map(ApprovalQuoteItemResponse::from)
                        .toList())
                .reasons(reasons.stream()
                        .map(ApprovalReasonResponse::from)
                        .toList())
                .histories(histories.stream()
                        .map(ApprovalHistoryResponse::from)
                        .toList())
                .build();
    }

    // 승인 상세 화면용 품목 요약 (원가 등 내부 정보는 제외)
    @Getter
    @Builder
    public static class ApprovalQuoteItemResponse {
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;

        public static ApprovalQuoteItemResponse from(QuoteItem item) {
            return ApprovalQuoteItemResponse.builder()
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .lineTotal(item.getLineTotal())
                    .build();
        }
    }
}