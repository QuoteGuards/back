package com.project.back.domain.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

// 조회수 기반 인기 제품 순위 (누적 조회수)
@Getter
@Builder
public class ProductViewRankResponse {
    private Long productId;
    private String productName;
    private long viewCount;
}
