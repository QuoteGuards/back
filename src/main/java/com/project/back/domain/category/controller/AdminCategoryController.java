package com.project.back.domain.category.controller;


import com.project.back.domain.category.dto.request.CategoryCreateRequest;
import com.project.back.domain.category.dto.request.CategoryUpdateRequest;
import com.project.back.domain.category.dto.response.CategoryResponse;
import com.project.back.domain.category.dto.response.CategoryTreeResponse;
import com.project.back.domain.category.service.CategoryService;
import com.project.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 최고 관리자에 필요한 카테고리 관리 기능 api
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    // 카테고리 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getCategoryList(){
        return ResponseEntity
                .ok(ApiResponse.success("카테고리 목록 조회 성공", categoryService.getCategoryList()));
    }

    // 카테고리 등록
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @RequestBody @Valid CategoryCreateRequest request){
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("카테고리 등록 성공", categoryService.create(request)));

    }

    // 카테고리 업데이트
    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long categoryId,
            @RequestBody @Valid CategoryUpdateRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success("카테고리가 수정 성공", categoryService.update(categoryId, request)));
    }

    // 카테고리 활성화
    @PatchMapping("/{categoryId}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(
            @PathVariable Long categoryId){
        categoryService.activate(categoryId);
        return ResponseEntity
                .ok(ApiResponse.success("카테고리 활성화 성공", null));
    }

    // 카테고리 비활성화
    @PatchMapping("/{categoryId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable Long categoryId){
        categoryService.deactivate(categoryId);
        return ResponseEntity
                .ok(ApiResponse.success("카테고리 비활성화 성공", null));
    }

    // 카테고리 삭제
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long categoryId){
        categoryService.delete(categoryId);
        return ResponseEntity
                .ok(ApiResponse.success("카테고리 삭제 성공", null));
    }
}
