package com.project.back.domain.product.controller;

import com.project.back.domain.product.service.ProductFavoriteService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/favorites")
@RequiredArgsConstructor
public class ProductFavoriteController {

    private final ProductFavoriteService productFavoriteService;

    // 즐겨찾기 추가
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> addFavorite(
            @PathVariable Long productId,
            @AuthenticationPrincipal Long userId
    ) {
        productFavoriteService.addFavorite(userId, productId);
        return ResponseEntity.ok(ApiResponse.success("즐겨찾기 추가 성공", null));
    }

    // 즐겨찾기 취소
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @PathVariable Long productId,
            @AuthenticationPrincipal Long userId
    ) {
        productFavoriteService.removeFavorite(userId, productId);
        return ResponseEntity.ok(ApiResponse.success("즐겨찾기 취소 성공", null));
    }
}