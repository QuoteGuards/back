package com.project.back.domain.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

// 반려/재요청 시점의 견적 내용을 QuoteApprovalHistory.quoteSnapshot(JSON)으로 직렬화하기 위한 DTO
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteSnapshotDto {

    private BigDecimal totalAmount;
    private BigDecimal profitRate;
    private BigDecimal discountRate;
    private List<ItemSnapshot> items;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemSnapshot {
        private Long productId;
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
    }
}
