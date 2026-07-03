package com.project.back.domain.approval.dto.response;

import com.project.back.domain.approval.dto.QuoteSnapshotDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 반려 시점 스냅샷과 재요청 시점 스냅샷을 비교한 변경 내역
@Getter
@Builder
public class QuoteDiffResponse {

    private BigDecimal totalAmountBefore;
    private BigDecimal totalAmountAfter;
    private BigDecimal profitRateBefore;
    private BigDecimal profitRateAfter;
    private BigDecimal discountRateBefore;
    private BigDecimal discountRateAfter;

    private List<ItemChange> addedItems;
    private List<ItemChange> removedItems;
    private List<QuantityChange> quantityChangedItems;

    // before/after 두 스냅샷을 비교해 diff를 계산한다.
    // 품목은 productId로 매칭한다 (QuoteService.updateQuote가 품목을 교체할 때 쓰는 식별 기준과 동일).
    // productId가 없는 품목(직접 입력한 품목 등)은 매칭 대상에서 제외한다.
    public static QuoteDiffResponse of(QuoteSnapshotDto before, QuoteSnapshotDto after) {
        Map<Long, QuoteSnapshotDto.ItemSnapshot> beforeByProduct = toProductMap(before.getItems());
        Map<Long, QuoteSnapshotDto.ItemSnapshot> afterByProduct = toProductMap(after.getItems());

        List<ItemChange> added = afterByProduct.entrySet().stream()
                .filter(e -> !beforeByProduct.containsKey(e.getKey()))
                .map(e -> ItemChange.of(e.getValue()))
                .toList();

        List<ItemChange> removed = beforeByProduct.entrySet().stream()
                .filter(e -> !afterByProduct.containsKey(e.getKey()))
                .map(e -> ItemChange.of(e.getValue()))
                .toList();

        List<QuantityChange> quantityChanged = afterByProduct.entrySet().stream()
                .filter(e -> beforeByProduct.containsKey(e.getKey()))
                .filter(e -> beforeByProduct.get(e.getKey()).getQuantity()
                        .compareTo(e.getValue().getQuantity()) != 0)
                .map(e -> new QuantityChange(
                        e.getValue().getProductName(),
                        beforeByProduct.get(e.getKey()).getQuantity(),
                        e.getValue().getQuantity()))
                .toList();

        return QuoteDiffResponse.builder()
                .totalAmountBefore(before.getTotalAmount())
                .totalAmountAfter(after.getTotalAmount())
                .profitRateBefore(before.getProfitRate())
                .profitRateAfter(after.getProfitRate())
                .discountRateBefore(before.getDiscountRate())
                .discountRateAfter(after.getDiscountRate())
                .addedItems(added)
                .removedItems(removed)
                .quantityChangedItems(quantityChanged)
                .build();
    }

    private static Map<Long, QuoteSnapshotDto.ItemSnapshot> toProductMap(List<QuoteSnapshotDto.ItemSnapshot> items) {
        return items.stream()
                .filter(item -> item.getProductId() != null)
                .collect(Collectors.toMap(
                        QuoteSnapshotDto.ItemSnapshot::getProductId, Function.identity(), (a, b) -> a));
    }

    @Getter
    @AllArgsConstructor
    public static class ItemChange {
        private String productName;
        private BigDecimal quantity;

        public static ItemChange of(QuoteSnapshotDto.ItemSnapshot item) {
            return new ItemChange(item.getProductName(), item.getQuantity());
        }
    }

    @Getter
    @AllArgsConstructor
    public static class QuantityChange {
        private String productName;
        private BigDecimal before;
        private BigDecimal after;
    }
}
