package com.project.back.domain.dashboard.dto;

import com.project.back.global.enums.QuoteStatus;

// 견적 상태별 건수 집계 projection
public record StatusCountRow(
        QuoteStatus status,
        Long count
) {}
