package com.project.back.domain.dashboard.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record SalesAnalysisResponse(

    // 총 견적 수
    long totalQuotes,

    // 승인 완료 견적 수
    long approvedQuotes,

    // 반려 견적 수
    long rejectedQuotes,

    // 고객 발송 완료 견적 수
    long sentQuotes,

    // 총 견적 금액
    BigDecimal totalAmount,

    // 총 예상 이익금
    BigDecimal totalProfitAmount,

    // 평균 할인율
    BigDecimal averageDiscountRate,

    // 평균 이익률
    BigDecimal averageProfitRate,

    // 승인율
    BigDecimal approvalRate,

    // 반려율
    BigDecimal rejectionRate,

    // 영업 현황 요약 문장
    String summary,

    // 개선 제안 문장
    String recommendation
) {

}
