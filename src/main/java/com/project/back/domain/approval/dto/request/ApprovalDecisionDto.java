package com.project.back.domain.approval.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApprovalDecisionDto {

    @NotNull(message = "승인 요청 ID는 필수입니다.")
    private Long approvalRequestId;

    // 반려 사유 (반려 시 필수, 승인 시 선택)
    private String rejectReason;

    // 승인 메모 (선택)
    private String memo;
}