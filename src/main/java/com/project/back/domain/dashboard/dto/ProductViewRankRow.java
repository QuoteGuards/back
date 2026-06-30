package com.project.back.domain.dashboard.dto;

// 조회수 기반 인기 제품 순위 projection (products.view_count 기준, 누적)
public record ProductViewRankRow(
        Long productId,
        String productName,
        Integer viewCount
) {}
