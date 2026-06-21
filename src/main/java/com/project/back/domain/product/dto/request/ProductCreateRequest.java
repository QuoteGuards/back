package com.project.back.domain.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;

// 제품 등록시 request
@Getter
public class ProductCreateRequest {

    @NotNull
    private Long categoryId;

    @NotBlank
    @Size(max=255)
    private String name;

    @NotBlank
    @Size(max=100)
    private String code;

    private String description;

    @Size(max=100)
    private String spec;

    @Size(max=500)
    private String imageUrl;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal unitPrice;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal costPrice;

    @NotBlank
    @Size(max = 20)
    private String unit;

    private boolean vatApplicable;

}
