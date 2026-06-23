package com.project.back.domain.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 최고관리자, 영업관리자 조회 위함
@Getter
@Builder
public class DashboardSummaryResponse {
    private long totalQuotes;             // 전체 견적 수
    private long approvedQuotes;          // 승인된 견적 수
    private long rejectedQuotes;          // 반려된 견적 수
    private long sentQuotes;              // 고객 발송 완료 견적 수
    private BigDecimal totalAmount;       // 총 견적 금액(VAT 포함)
    private BigDecimal totalSupplyAmount; // 총 공급가액
    private BigDecimal totalProfitAmount; // 총 예상 이익금
    private BigDecimal averageDiscountRate; // 평균 할인율(%)
    private BigDecimal averageProfitRate;   // 평균 이익률(%)
}
