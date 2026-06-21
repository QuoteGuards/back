package com.project.back.domain.category.dto.response;

import com.project.back.domain.category.entity.Category;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CategoryResponse {

    private Long id;
    private Long parentId;
    private String name;
    private String slug;
    private int depth;
    private int sortOrder;
    private boolean isActive;
    private LocalDateTime createdAt;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .name(category.getName())
                .slug(category.getSlug())
                .depth(category.getDepth())
                .sortOrder(category.getSortOrder())
                .isActive(category.isActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}