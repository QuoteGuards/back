package com.project.back.domain.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 영업사원별 통계 (작성 건수 / 승인율 / 반려율)
@Getter
@Builder
public class SalesStaffResponse {
    private Long userId;
    private String userName;
    private long totalQuotes;       // 작성 건수
    private long approvedQuotes;    // 승인 건수
    private long rejectedQuotes;    // 반려 건수
    private BigDecimal approvalRate;  // 승인율(%) = 승인/전체작성(totalQuotes)
    private BigDecimal rejectionRate; // 반려율(%) = 반려/전체작성(totalQuotes)
}
