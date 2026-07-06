package com.project.back.domain.quote.entity;

import com.project.back.domain.discount.entity.DiscountPolicy;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "quote_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_policy_id")
    private DiscountPolicy discountPolicy;

    /** 견적 작성 당시 policy.maxDiscountRate 스냅샷 (마스터 변경·삭제와 무관하게 검증·감사) */
    @Column(name = "policy_max_discount_rate", precision = 5, scale = 2)
    private BigDecimal policyMaxDiscountRate;

    /** 견적 작성 당시 policy.minProfitRate 스냅샷 */
    @Column(name = "policy_min_profit_rate", precision = 5, scale = 2)
    private BigDecimal policyMinProfitRate;

    /** 견적 작성 당시 policy.approvalThresholdAmount 스냅샷 */
    @Column(name = "policy_approval_threshold_amount", precision = 18, scale = 2)
    private BigDecimal policyApprovalThresholdAmount;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_code", length = 100)
    private String productCode;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

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

    @Column(name = "line_supply_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal lineSupplyAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(length = 200)
    private String spec;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void updateCalculation(BigDecimal discountAmount, BigDecimal lineSupplyAmount,
                                  BigDecimal vatAmount, BigDecimal lineTotal, String discountReason) {
        this.discountAmount = discountAmount;
        this.lineSupplyAmount = lineSupplyAmount;
        this.vatAmount = vatAmount;
        this.lineTotal = lineTotal;
        this.discountReason = discountReason;
    }

    public void updateQuantityAndDiscount(BigDecimal quantity, BigDecimal discountRate) {
        this.quantity = quantity;
        this.discountRate = discountRate;
    }

    public void assignQuote(Quote quote) {
        this.quote = quote;
    }

    public void assignDiscountPolicy(DiscountPolicy discountPolicy) {
        this.discountPolicy = discountPolicy;
        if (discountPolicy != null) {
            this.policyMaxDiscountRate = discountPolicy.getMaxDiscountRate();
            this.policyMinProfitRate = discountPolicy.getMinProfitRate();
            this.policyApprovalThresholdAmount = discountPolicy.getApprovalThresholdAmount();
        } else {
            this.policyMaxDiscountRate = null;
            this.policyMinProfitRate = null;
            this.policyApprovalThresholdAmount = null;
        }
    }

    /** 스냅샷 우선, 구 데이터는 FK policy로 폴백 */
    public BigDecimal getEffectiveMaxDiscountRate() {
        if (policyMaxDiscountRate != null) {
            return policyMaxDiscountRate;
        }
        return discountPolicy != null ? discountPolicy.getMaxDiscountRate() : null;
    }

    public BigDecimal getEffectiveMinProfitRate() {
        if (policyMinProfitRate != null) {
            return policyMinProfitRate;
        }
        return discountPolicy != null ? discountPolicy.getMinProfitRate() : null;
    }

    public BigDecimal getEffectiveApprovalThresholdAmount() {
        if (policyApprovalThresholdAmount != null) {
            return policyApprovalThresholdAmount;
        }
        return discountPolicy != null ? discountPolicy.getApprovalThresholdAmount() : null;
    }
}