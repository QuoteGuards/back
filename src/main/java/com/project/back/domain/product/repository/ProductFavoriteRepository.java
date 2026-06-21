package com.project.back.domain.product.repository;

import com.project.back.domain.product.entity.ProductFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

public interface ProductFavoriteRepository extends JpaRepository<ProductFavorite, Long> {

    Optional<ProductFavorite> findByUserIdAndProductId(Long userId, Long productId);

    // 즐겨찾기 여부 확인
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    // 특정 사용자의 즐겨찾기 product id 목록 (목록 조회 시 즐겨찾기 여부 표시용)
    Set<Long> findProductIdByUserId(Long userId);
}