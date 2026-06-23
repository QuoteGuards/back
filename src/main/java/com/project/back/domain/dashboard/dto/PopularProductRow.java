package com.project.back.domain.dashboard.dto;

import java.math.BigDecimal;

// 인기 제품 집계 projection (quote_items 기준)
public record PopularProductRow(
        Long productId,
        String productName,
        Long orderCount,           // 견적 포함 건수
        BigDecimal totalQuantity,  // 총 주문 수량
        BigDecimal totalSalesAmount // 매출 기여액(line_total 합)
) {}
