package com.project.back.domain.approval.dto.response;

import com.project.back.domain.approval.entity.QuoteApprovalReason;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApprovalReasonResponse {

    private Long id;
    private Long quoteId;
    private String reasonType;
    private String message;
    private LocalDateTime createdAt;

    // Entity → DTO 변환 메서드
    public static ApprovalReasonResponse from(QuoteApprovalReason entity) {
        return ApprovalReasonResponse.builder()
                .id(entity.getId())
                .quoteId(entity.getQuote().getId())
                .reasonType(entity.getReasonType().name())
                .message(entity.getReasonMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}