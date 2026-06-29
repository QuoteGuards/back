package com.project.back.domain.product.controller;

import com.project.back.domain.product.dto.request.ProductBulkRequest;
import com.project.back.domain.product.dto.request.ProductCreateRequest;
import com.project.back.domain.product.dto.request.ProductUpdateRequest;
import com.project.back.domain.product.dto.response.ProductResponse;
import com.project.back.domain.product.service.ProductService;
import com.project.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// 관리자용
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    // 제품 조회
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProductList(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean vatApplicable,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "제품 목록 조회 성공",
                productService.getProductList(categoryId, keyword, isActive, vatApplicable, pageable)
        ));
    }

    // 제품 상세 조회
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(
                "제품 상세 조회 성공",
                productService.getProduct(productId)
        ));
    }

    // 제품 등록
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @RequestBody @Valid ProductCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "제품 등록 성공",
                productService.create(request)
        ));
    }

    // 제품 수정
    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable Long productId,
            @RequestBody @Valid ProductUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "제품 수정 성공",
                productService.update(productId, request)
        ));
    }

    // 제품 활성화
    @PatchMapping("/{productId}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long productId) {
        productService.activate(productId);
        return ResponseEntity.ok(ApiResponse.success("제품 활성화 성공", null));
    }

    // 제품 비활성화
    @PatchMapping("/{productId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long productId) {
        productService.deactivate(productId);
        return ResponseEntity.ok(ApiResponse.success("제품 비활성화 성공", null));
    }

    // 제품 삭제
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long productId) {
        productService.delete(productId);
        return ResponseEntity.ok(ApiResponse.success("제품 삭제 성공", null));
    }

    // ── 일괄 처리 (체크박스 선택 → 한 번에) ──
    @PatchMapping("/bulk/activate")
    public ResponseEntity<ApiResponse<Void>> bulkActivate(@RequestBody @Valid ProductBulkRequest request) {
        productService.bulkActivate(request.getIds());
        return ResponseEntity.ok(ApiResponse.success("제품 일괄 활성화 성공", null));
    }

    @PatchMapping("/bulk/deactivate")
    public ResponseEntity<ApiResponse<Void>> bulkDeactivate(@RequestBody @Valid ProductBulkRequest request) {
        productService.bulkDeactivate(request.getIds());
        return ResponseEntity.ok(ApiResponse.success("제품 일괄 비활성화 성공", null));
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<ApiResponse<Void>> bulkDelete(@RequestBody @Valid ProductBulkRequest request) {
        productService.bulkDelete(request.getIds());
        return ResponseEntity.ok(ApiResponse.success("제품 일괄 삭제 성공", null));
    }
}