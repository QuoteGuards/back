package com.project.back.domain.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

// 견적 상태별 건수 (상태별 차트용 — 전체 상태를 0 포함하여 반환)
@Getter
@Builder
public class QuoteStatusCountResponse {
    private String status;   // QuoteStatus name (DRAFT, APPROVED, ...)
    private long count;
}
