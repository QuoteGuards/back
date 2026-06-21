package com.project.back.domain.category.dto.response;

import com.project.back.domain.category.entity.Category;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class CategoryTreeResponse {
    // 전체 트리 조회할 때 사용

    private Long id;
    private Long parentId;
    private String name;
    private String slug;
    private int depth;
    private int sortOrder;
    private boolean isActive;
    private long productCount;

    @Builder.Default
    private List<CategoryTreeResponse> children = new ArrayList<>();

    public void addChild(CategoryTreeResponse child){
        this.children.add(child);
    }

    public static CategoryTreeResponse from(Category category, long productCount) {
        return CategoryTreeResponse.builder()
                .id(category.getId())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .name(category.getName())
                .slug(category.getSlug())
                .depth(category.getDepth())
                .sortOrder(category.getSortOrder())
                .isActive(category.isActive())
                .productCount(productCount)
                .build();
    }
}
