package com.project.back.domain.approval.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApprovalRequestDto {

    @NotNull(message = "견적 ID는 필수입니다.")
    private Long quoteId;

    // 승인 요청 사유 (선택)
    private String requestMemo;
}