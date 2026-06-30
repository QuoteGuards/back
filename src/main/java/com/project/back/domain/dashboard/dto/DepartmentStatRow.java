package com.project.back.domain.dashboard.dto;

import java.math.BigDecimal;

// 부서별 견적 집계 projection (작성자 User.department 기준)
public record DepartmentStatRow(
        String department,
        Long totalQuotes,
        Long approvedQuotes,
        Long rejectedQuotes,
        BigDecimal totalAmount
) {}
