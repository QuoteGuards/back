package com.project.back.domain.product.controller;

import com.project.back.domain.product.dto.response.ProductSearchResponse;
import com.project.back.domain.product.service.ProductFavoriteService;
import com.project.back.domain.product.service.ProductService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductFavoriteService productFavoriteService;

    // 제품 조회
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductSearchResponse>>> searchProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "제품 목록 조회 성공",
                productService.searchProducts(categoryId, keyword, userId, pageable)
        ));
    }

    // 즐겨찾기 목록 조회
    // 쿼리 루트가 ProductFavorite라 sort 기본값을 pf의 컬럼(createdAt)으로 둬야 함
    // (product 컬럼명인 "name"을 기본값으로 두면 pf.name 으로 해석돼 500 발생)
    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<Page<ProductSearchResponse>>> getFavorites(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "즐겨찾기 목록 조회 성공",
                productFavoriteService.getFavorites(userId, pageable)
        ));
    }

    // 즐겨찾기 전체 해제 (벌크 — 개별 DELETE N번 대신 트랜잭션 1번)
    @DeleteMapping("/favorites")
    public ResponseEntity<ApiResponse<Void>> removeAllFavorites(
            @AuthenticationPrincipal Long userId
    ) {
        productFavoriteService.removeAllFavorites(userId);
        return ResponseEntity.ok(ApiResponse.success("즐겨찾기 전체 해제 성공", null));
    }

    // 제품 상세 조회
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductSearchResponse>> getProductDetail(
            @PathVariable Long productId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "제품 상세 조회 성공",
                productService.getProductDetail(productId, userId)
        ));
    }
}