package com.project.back.domain.approval.dto.response;

import com.project.back.domain.approval.entity.QuoteApprovalHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApprovalHistoryResponse {

    private Long id;

    // 처리한 사람
    private Long actorId;
    private String actorName;

    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String memo;

    private LocalDateTime actedAt;

    // Entity → DTO 변환 메서드
    public static ApprovalHistoryResponse from(QuoteApprovalHistory entity) {
        return ApprovalHistoryResponse.builder()
                .id(entity.getId())
                .actorId(entity.getActor().getId())
                .actorName(entity.getActor().getName())
                .action(entity.getAction().name())
                .beforeStatus(entity.getBeforeStatus() != null
                        ? entity.getBeforeStatus().name() : null)
                .afterStatus(entity.getAfterStatus() != null
                        ? entity.getAfterStatus().name() : null)
                .memo(entity.getMemo())
                .actedAt(entity.getActedAt())
                .build();
    }
}