package com.project.back.domain.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 부서별 통계 (작성 건수 / 승인율 / 반려율 / 견적 총액)
@Getter
@Builder
public class DepartmentStatResponse {
    private String department;        // 부서명 (없으면 "미지정")
    private long totalQuotes;         // 작성 건수
    private long approvedQuotes;      // 승인 건수
    private long rejectedQuotes;      // 반려 건수
    private BigDecimal approvalRate;   // 승인율(%) = 승인/(승인+반려)
    private BigDecimal rejectionRate;  // 반려율(%) = 반려/(승인+반려)
    private BigDecimal totalAmount;    // 견적 총액(VAT 포함)
}
