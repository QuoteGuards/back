package com.project.back.domain.product.service;

import com.project.back.domain.product.dto.response.ProductSearchResponse;
import com.project.back.domain.product.entity.Product;
import com.project.back.domain.product.entity.ProductFavorite;
import com.project.back.domain.product.repository.ProductFavoriteRepository;
import com.project.back.domain.product.repository.ProductRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductFavoriteService {

    private final ProductFavoriteRepository productFavoriteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // 즐겨찾기 추가
    @Transactional
    public void addFavorite(Long userId, Long productId) {
        if (productFavoriteRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CustomException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        productFavoriteRepository.save(ProductFavorite.of(user, product));
    }

    // 즐겨찾기 취소
    @Transactional
    public void removeFavorite(Long userId, Long productId) {
        ProductFavorite favorite = productFavoriteRepository
                .findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAVORITE_NOT_FOUND));

        productFavoriteRepository.delete(favorite);
    }

    // 즐겨찾기 전체 해제 (벌크) — 해당 사용자의 즐겨찾기를 쿼리 1번으로 일괄 삭제
    @Transactional
    public void removeAllFavorites(Long userId) {
        productFavoriteRepository.deleteAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<ProductSearchResponse> getFavorites(Long userId, Pageable pageable) {
        return productFavoriteRepository.findActiveProductsByUserId(userId, pageable)
                .map(product -> ProductSearchResponse.of(product, true));
    }
}