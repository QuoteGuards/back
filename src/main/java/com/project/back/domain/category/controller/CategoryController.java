package com.project.back.domain.category.controller;

import com.project.back.domain.category.dto.response.CategoryResponse;
import com.project.back.domain.category.dto.response.CategoryTreeResponse;
import com.project.back.domain.category.service.CategoryService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 영업사원이 카테고리 조회를 위함
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // privateId의 하위분류(중분류 혹은 소분류) 조회
    // null 값이면 대분류 조회
    // 활성화되어잇는 분류들만 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoryList(
            @RequestParam(required=false) Long parentId) {
        return ResponseEntity
                .ok(ApiResponse.success("카테고리 목록 조회 성공", categoryService.getActiveChildren(parentId)));
    }

    // 활성 카테고리 전체 트리 조회 (드릴다운 N+1 제거: 한 번에 조회)
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getActiveCategoryTree() {
        return ResponseEntity
                .ok(ApiResponse.success("활성 카테고리 트리 조회 성공", categoryService.getActiveCategoryTree()));
    }
}
