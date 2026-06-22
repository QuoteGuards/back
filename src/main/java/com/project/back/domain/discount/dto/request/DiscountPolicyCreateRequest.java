package com.project.back.domain.discount.dto.request;

import com.project.back.global.enums.DiscountTargetType;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class DiscountPolicyCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private DiscountTargetType targetType;
    // ALL / CATEGORY / PRODUCT

    private Long categoryId;   // targetType=CATEGORY 일 때 필수
    private Long productId;     // targetType=PRODUCT 일 때 필수

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal maxDiscountRate;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal minProfitRate;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal highAmountThreshold;

    private LocalDateTime effectiveFrom;   // null이면 현재 시각
    private LocalDateTime effectiveTo;      // null이면 종료일 없음
}