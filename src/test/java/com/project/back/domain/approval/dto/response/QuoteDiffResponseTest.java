package com.project.back.domain.approval.dto.response;

import com.project.back.domain.approval.dto.QuoteSnapshotDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuoteDiffResponse.of - 견적 스냅샷 비교")
class QuoteDiffResponseTest {

    private QuoteSnapshotDto.ItemSnapshot item(Long productId, String name, long quantity, long unitPrice) {
        return QuoteSnapshotDto.ItemSnapshot.builder()
                .productId(productId)
                .productName(name)
                .quantity(BigDecimal.valueOf(quantity))
                .unitPrice(BigDecimal.valueOf(unitPrice))
                .build();
    }

    @Test
    @DisplayName("총액/이익률/할인율의 전후 값을 그대로 담는다")
    void of_carriesBeforeAfterAmounts() {
        QuoteSnapshotDto before = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.valueOf(10_000_000))
                .profitRate(BigDecimal.valueOf(18.0))
                .discountRate(BigDecimal.valueOf(15.0))
                .items(List.of())
                .build();
        QuoteSnapshotDto after = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.valueOf(12_000_000))
                .profitRate(BigDecimal.valueOf(21.5))
                .discountRate(BigDecimal.valueOf(12.3))
                .items(List.of())
                .build();

        QuoteDiffResponse diff = QuoteDiffResponse.of(before, after);

        assertThat(diff.getTotalAmountBefore()).isEqualByComparingTo(BigDecimal.valueOf(10_000_000));
        assertThat(diff.getTotalAmountAfter()).isEqualByComparingTo(BigDecimal.valueOf(12_000_000));
        assertThat(diff.getProfitRateBefore()).isEqualByComparingTo(BigDecimal.valueOf(18.0));
        assertThat(diff.getProfitRateAfter()).isEqualByComparingTo(BigDecimal.valueOf(21.5));
        assertThat(diff.getDiscountRateBefore()).isEqualByComparingTo(BigDecimal.valueOf(15.0));
        assertThat(diff.getDiscountRateAfter()).isEqualByComparingTo(BigDecimal.valueOf(12.3));
    }

    @Test
    @DisplayName("after에만 있는 productId는 추가된 품목으로 분류한다")
    void of_detectsAddedItem() {
        QuoteSnapshotDto before = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(1L, "노트북", 1, 1_000_000)))
                .build();
        QuoteSnapshotDto after = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(1L, "노트북", 1, 1_000_000), item(2L, "마우스", 5, 20_000)))
                .build();

        QuoteDiffResponse diff = QuoteDiffResponse.of(before, after);

        assertThat(diff.getAddedItems()).hasSize(1);
        assertThat(diff.getAddedItems().get(0).getProductName()).isEqualTo("마우스");
        assertThat(diff.getAddedItems().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(diff.getRemovedItems()).isEmpty();
        assertThat(diff.getQuantityChangedItems()).isEmpty();
    }

    @Test
    @DisplayName("before에만 있는 productId는 삭제된 품목으로 분류한다")
    void of_detectsRemovedItem() {
        QuoteSnapshotDto before = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(1L, "노트북", 1, 1_000_000), item(2L, "마우스", 5, 20_000)))
                .build();
        QuoteSnapshotDto after = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(1L, "노트북", 1, 1_000_000)))
                .build();

        QuoteDiffResponse diff = QuoteDiffResponse.of(before, after);

        assertThat(diff.getRemovedItems()).hasSize(1);
        assertThat(diff.getRemovedItems().get(0).getProductName()).isEqualTo("마우스");
        assertThat(diff.getAddedItems()).isEmpty();
        assertThat(diff.getQuantityChangedItems()).isEmpty();
    }

    @Test
    @DisplayName("동일 productId의 수량이 바뀌면 변경 항목으로 분류하고, 안 바뀌면 무시한다")
    void of_detectsQuantityChangeOnly() {
        QuoteSnapshotDto before = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(1L, "노트북", 1, 1_000_000), item(2L, "마우스", 5, 20_000)))
                .build();
        QuoteSnapshotDto after = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(1L, "노트북", 2, 1_000_000), item(2L, "마우스", 5, 20_000)))
                .build();

        QuoteDiffResponse diff = QuoteDiffResponse.of(before, after);

        assertThat(diff.getQuantityChangedItems()).hasSize(1);
        assertThat(diff.getQuantityChangedItems().get(0).getProductName()).isEqualTo("노트북");
        assertThat(diff.getQuantityChangedItems().get(0).getBefore()).isEqualByComparingTo(BigDecimal.valueOf(1));
        assertThat(diff.getQuantityChangedItems().get(0).getAfter()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(diff.getAddedItems()).isEmpty();
        assertThat(diff.getRemovedItems()).isEmpty();
    }

    @Test
    @DisplayName("productId가 없고 내용도 안 바뀐 품목은 변경 없음으로 처리한다")
    void of_noChangeForUnchangedItemWithoutProductId() {
        QuoteSnapshotDto before = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(null, "직접입력품목", 1, 5000)))
                .build();
        QuoteSnapshotDto after = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(null, "직접입력품목", 1, 5000)))
                .build();

        QuoteDiffResponse diff = QuoteDiffResponse.of(before, after);

        assertThat(diff.getAddedItems()).isEmpty();
        assertThat(diff.getRemovedItems()).isEmpty();
        assertThat(diff.getQuantityChangedItems()).isEmpty();
    }

    @Test
    @DisplayName("productId가 없는 품목은 productName으로 대체 매칭해서 수량 변경을 감지한다")
    void of_matchesByNameWhenProductIdMissing() {
        QuoteSnapshotDto before = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(null, "직접입력품목", 1, 5000)))
                .build();
        QuoteSnapshotDto after = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(null, "직접입력품목", 3, 5000)))
                .build();

        QuoteDiffResponse diff = QuoteDiffResponse.of(before, after);

        assertThat(diff.getQuantityChangedItems()).hasSize(1);
        assertThat(diff.getQuantityChangedItems().get(0).getProductName()).isEqualTo("직접입력품목");
        assertThat(diff.getQuantityChangedItems().get(0).getBefore()).isEqualByComparingTo(BigDecimal.valueOf(1));
        assertThat(diff.getQuantityChangedItems().get(0).getAfter()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(diff.getAddedItems()).isEmpty();
        assertThat(diff.getRemovedItems()).isEmpty();
    }

    @Test
    @DisplayName("productId가 없는 품목끼리는 이름이 다르면 서로 다른 품목(추가/삭제)으로 본다")
    void of_treatsDifferentNamesAsAddedAndRemovedWhenProductIdMissing() {
        QuoteSnapshotDto before = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(null, "직접입력품목A", 1, 5000)))
                .build();
        QuoteSnapshotDto after = QuoteSnapshotDto.builder()
                .totalAmount(BigDecimal.ZERO).profitRate(BigDecimal.ZERO).discountRate(BigDecimal.ZERO)
                .items(List.of(item(null, "직접입력품목B", 1, 5000)))
                .build();

        QuoteDiffResponse diff = QuoteDiffResponse.of(before, after);

        assertThat(diff.getAddedItems()).extracting("productName").containsExactly("직접입력품목B");
        assertThat(diff.getRemovedItems()).extracting("productName").containsExactly("직접입력품목A");
        assertThat(diff.getQuantityChangedItems()).isEmpty();
    }
}
