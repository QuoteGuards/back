package com.project.back.domain.product.dto.response;

import com.project.back.domain.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 관리자용 제품 response
@Getter
@Builder
public class ProductResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String code;
    private String description;
    private String spec;
    private String imageUrl;
    private BigDecimal unitPrice;
    private BigDecimal costPrice;
    private String unit;
    private boolean vatApplicable;
    private boolean isActive;
    private int viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .code(product.getCode())
                .description(product.getDescription())
                .spec(product.getSpec())
                .imageUrl(product.getImageUrl())
                .unitPrice(product.getUnitPrice())
                .costPrice(product.getCostPrice())
                .unit(product.getUnit())
                .vatApplicable(product.isVatApplicable())
                .isActive(product.isActive())
                .viewCount(product.getViewCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
