package com.project.back.domain.discount.entity;

import com.project.back.domain.category.entity.Category;
import com.project.back.domain.product.entity.Product;
import com.project.back.global.enums.DiscountTargetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 할인 정책 엔티티
 * 관리: DiscountPolicyService(제품, 카테고리 파트)
 * 사용: ApprovalCheckService(견적 승인 필요 여부 판단시에 사용)
 */
@Entity
@Table(name = "discount_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DiscountPolicy {

    // 할인정책 id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 정책이름 (DB 컬럼은 SQL 스키마대로 name)
    @Column(name = "name", nullable = false, length = 100)
    private String policyName;

    // 정책적용 대상
    @Enumerated(EnumType.STRING)
    @Column(name="target_type", nullable=false, length=20)
    @Builder.Default
    private DiscountTargetType targetType = DiscountTargetType.ALL;

    // target_type = CATEGORY 일 때만 사용 (ON DELETE SET NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // target_type = PRODUCT 일 때만 사용 (ON DELETE SET NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // 승인 없이 허용되는 최대 할인율 (%)
    // ex) 10.00 → 10% 초과 할인 시 DISCOUNT_EXCEEDED
    @Column(name = "max_discount_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxDiscountRate = BigDecimal.ZERO;

    // 승인 없이 허용되는 최소 이익률 (%)
    // ex) 15.00 → 이익률 15% 미만 시 LOW_PROFIT
    @Column(name = "min_profit_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal minProfitRate = BigDecimal.ZERO;

    // 이 금액 초과 시 승인 필요 (고액 견적)
    // ex) 10000000 → 1천만원 초과 시 HIGH_AMOUNT
    @Column(name = "high_amount_threshold", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal approvalThresholdAmount = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(String name, DiscountTargetType targetType,
                       Category category, Product product,
                       BigDecimal maxDiscountRate, BigDecimal minProfitRate,
                       BigDecimal approvalThresholdAmount,
                       LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        this.policyName = name;
        this.targetType = targetType;
        this.category = category;
        this.product = product;
        this.maxDiscountRate = maxDiscountRate;
        this.minProfitRate = minProfitRate;
        this.approvalThresholdAmount = approvalThresholdAmount;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public void activate() { this.isActive = true; }
    public void deactivate() { this.isActive = false; }
}
