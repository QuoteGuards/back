package com.project.back.domain.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 인기 제품 순위 (견적 포함 건수 기준 내림차순)
@Getter
@Builder
public class PopularProductResponse {
    private Long productId;
    private String productName;
    private long orderCount;            // 견적 포함 건수
    private BigDecimal totalQuantity;   // 총 주문 수량
    private BigDecimal totalSalesAmount; // 매출 기여액
}
