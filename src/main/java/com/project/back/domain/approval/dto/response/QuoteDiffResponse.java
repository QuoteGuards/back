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
    // productId가 없는 품목(레거시 데이터 등)은 productName으로 대체 매칭한다.
    public static QuoteDiffResponse of(QuoteSnapshotDto before, QuoteSnapshotDto after) {
        Map<String, QuoteSnapshotDto.ItemSnapshot> beforeByKey = toKeyMap(before.getItems());
        Map<String, QuoteSnapshotDto.ItemSnapshot> afterByKey = toKeyMap(after.getItems());

        List<ItemChange> added = afterByKey.entrySet().stream()
                .filter(e -> !beforeByKey.containsKey(e.getKey()))
                .map(e -> ItemChange.of(e.getValue()))
                .toList();

        List<ItemChange> removed = beforeByKey.entrySet().stream()
                .filter(e -> !afterByKey.containsKey(e.getKey()))
                .map(e -> ItemChange.of(e.getValue()))
                .toList();

        List<QuantityChange> quantityChanged = afterByKey.entrySet().stream()
                .filter(e -> beforeByKey.containsKey(e.getKey()))
                .filter(e -> beforeByKey.get(e.getKey()).getQuantity()
                        .compareTo(e.getValue().getQuantity()) != 0)
                .map(e -> new QuantityChange(
                        e.getValue().getProductName(),
                        beforeByKey.get(e.getKey()).getQuantity(),
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

    // productId가 있으면 productId로, 없으면(레거시 데이터 등) productName으로 매칭 키를 만든다.
    private static String matchKey(QuoteSnapshotDto.ItemSnapshot item) {
        return item.getProductId() != null
                ? "id:" + item.getProductId()
                : "name:" + item.getProductName();
    }

    private static Map<String, QuoteSnapshotDto.ItemSnapshot> toKeyMap(List<QuoteSnapshotDto.ItemSnapshot> items) {
        return items.stream()
                .collect(Collectors.toMap(
                        QuoteDiffResponse::matchKey, Function.identity(), (a, b) -> a));
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
