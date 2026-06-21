package com.project.back.domain.product.dto.response;

import com.project.back.domain.product.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 영업사원용 제품 response
@Getter
@Builder
public class ProductSearchResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String code;
    private String description;
    private String spec;
    private String imageUrl;
    private BigDecimal unitPrice;
    private String unit;
    private boolean vatApplicable;
    private boolean isFavorite;
    private boolean isActive;

    public static ProductSearchResponse of(Product product, boolean isFavorite) {
        return ProductSearchResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .code(product.getCode())
                .description(product.getDescription())
                .spec(product.getSpec())
                .imageUrl(product.getImageUrl())
                .unitPrice(product.getUnitPrice())
                .unit(product.getUnit())
                .vatApplicable(product.isVatApplicable())
                .isFavorite(isFavorite)
                .isActive(product.isActive())
                .build();
    }
}