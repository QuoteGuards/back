package com.project.back.domain.discount.dto.request;

import com.project.back.global.enums.DiscountTargetType;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class DiscountPolicyUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private DiscountTargetType targetType;

    private Long categoryId;
    private Long productId;

    `@NotNull`
    `@DecimalMin`("0.00")
    `@DecimalMax`("100.00")
    `@Digits`(integer = 3, fraction = 2)
    private BigDecimal maxDiscountRate;

    `@NotNull`
    `@DecimalMin`("0.00")
    `@DecimalMax`("100.00")
    `@Digits`(integer = 3, fraction = 2)
    private BigDecimal minProfitRate;

    `@NotNull`
    `@DecimalMin`("0.00")
    `@Digits`(integer = 16, fraction = 2)
    private BigDecimal highAmountThreshold;

    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}