package com.project.back.domain.approval.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApprovalRequestDto {

    // 승인 요청 사유 (선택)
    private String requestMemo;
}
