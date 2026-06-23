package com.project.back.domain.dashboard.dto;

// 영업사원별 견적 집계 projection
public record SalesStaffRow(
        Long userId,
        String userName,
        Long totalQuotes,
        Long approvedQuotes,
        Long rejectedQuotes
) {}
