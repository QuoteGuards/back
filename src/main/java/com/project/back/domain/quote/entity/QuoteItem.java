package com.project.back.domain.quote.entity;

import jakarta.persistence.*;
import lombok.*;import rg.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@Table(name = "quote_items")
@Getter@NoArgsonstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builde public clas uoteItem {     @Id    @Geerat

    private Long id 
    @ManyToOne(fetch = FetchType.LAZY)     @JonColumn(name = "quote_id", nullable = false)
    private Quote quote;

    // 제품 삭제돼도 견적 항목은 유지 (ON DELETE SET NULL)
    @Column(name = "product_id")
    private Long productId;

    // ── 스냅샷 (견적 작성 당시 기준으로 고정) ────────
    // 제품명/단가/원가는 나중에 바뀌어도 견적서는 작성 당시 값 유지

    @Column(name = "prod

    
    @Column(name = "product_code", length = 100)
    private StrigproductCode;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    // ─────────────────────────────────────────────────

    // 소수 수량 지원 (KG, M 등)
    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "vat_applicable", nullable = false)
    @Builder.Default
    private Boolean vatApplicable = true;

    @Column(name = "vat_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    // VAT 제외 공급가액
    @Column(name = "line_supply_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal lineSupplyAmount = BigDecimal.ZERO;

    // 항목 최종 금액 (수량 * 단가 - 할인 + VAT)
    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 200)
    private String spec;

    public void updateCalculation(BigDecimal discountAmount, BigDecimal lineSupplyAmount,
                                  BigDecimal vatAmount, BigDecimal lineTotal) {
        this.discountAmount = discountAmount;
        this.lineSupplyAmount = lineSupplyAmount;
        this.vatAmount = vatAmount;
        this.lineTotal = lineTotal;
    }

            iscount(BigDecimal quantity, BigDecimal discountRate) {
        this.quantity = quantity;
        this.discountRate = discountRate;
    }
}


    