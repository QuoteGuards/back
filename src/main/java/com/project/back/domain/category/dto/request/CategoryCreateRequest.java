package com.project.back.domain.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CategoryCreateRequest {
    // 카테고리 생성 리퀘스트

    private Long parentId; // null이면 대분류

    @NotBlank(message = "카테고리명을 입력해주세요.")
    @Size(max = 100, message = "카테고리명은 100자 이하로 입력해주세요.")
    private String name;

    @NotBlank(message = "슬러그를 입력해주세요.")
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9-]+$", message = "슬러그는 영소문자, 숫자, 하이픈만 사용할 수 있습니다.")
    private String slug;

    private int sortOrder;
}