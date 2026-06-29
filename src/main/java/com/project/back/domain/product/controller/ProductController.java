package com.project.back.domain.product.controller;

import com.project.back.domain.product.dto.response.ProductSearchResponse;
import com.project.back.domain.product.service.ProductFavoriteService;
import com.project.back.domain.product.service.ProductService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    // 쿼리 루트가 ProductFavorite(pf)라 클라가 보낸 sort 키를 안전한 프로퍼티로 매핑해야 함
    // (product 컬럼명인 "name"을 그대로 두면 pf.name 으로 해석돼 500 발생)
    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<Page<ProductSearchResponse>>> getFavorites(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "즐겨찾기 목록 조회 성공",
                productFavoriteService.getFavorites(userId, sanitizeFavoriteSort(pageable))
        ));
    }

    // 클라가 보낸 정렬 키를 허용 목록으로 제한하고 pf 기준 안전한 경로로 매핑
    // 허용 외 키는 무시, 결과가 비면 createdAt 내림차순(최근 추가순)으로 폴백
    private static final Map<String, String> FAVORITE_SORT_KEYS = Map.of(
            "createdAt", "createdAt",          // pf 자체 컬럼
            "name", "product.name",            // product 경로로 매핑
            "code", "product.code",
            "unitPrice", "product.unitPrice"
    );

    private Pageable sanitizeFavoriteSort(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String mapped = FAVORITE_SORT_KEYS.get(order.getProperty());
            if (mapped != null) {
                orders.add(new Sort.Order(order.getDirection(), mapped));
            }
        }
        Sort sort = orders.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
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