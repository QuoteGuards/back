package com.project.back.domain.discount.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 할인 정책 엔티티
 * ApprovalCheckService에서 승인 필요 여부 판단 기준으로 사용
 * ※ 이 파일은 임시 작성한 스텁
 */
@Entity
@Table(name = "discount_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DiscountPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_name", nullable = false, length = 100)
    private String policyName;

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
    @Column(name = "approval_threshold_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal approvalThresholdAmount = new BigDecimal("100000000");

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
