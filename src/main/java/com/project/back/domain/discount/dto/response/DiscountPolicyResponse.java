package com.project.back.domain.discount.dto.response;

import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.global.enums.DiscountTargetType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class DiscountPolicyResponse {

    private Long id;
    private String name;
    private DiscountTargetType targetType;
    private Long categoryId;
    private String categoryName;
    private Long productId;
    private String productName;
    private BigDecimal maxDiscountRate;
    private BigDecimal minProfitRate;
    private BigDecimal highAmountThreshold;
    private Boolean isActive;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DiscountPolicyResponse from(DiscountPolicy p) {
        return DiscountPolicyResponse.builder()
                .id(p.getId())
                .name(p.getPolicyName())
                .targetType(p.getTargetType())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .productId(p.getProduct() != null ? p.getProduct().getId() : null)
                .productName(p.getProduct() != null ? p.getProduct().getName() : null)
                .maxDiscountRate(p.getMaxDiscountRate())
                .minProfitRate(p.getMinProfitRate())
                .highAmountThreshold(p.getApprovalThresholdAmount())
                .isActive(p.getIsActive())
                .effectiveFrom(p.getEffectiveFrom())
                .effectiveTo(p.getEffectiveTo())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}