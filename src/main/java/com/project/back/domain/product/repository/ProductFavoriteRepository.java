package com.project.back.domain.product.repository;

import com.project.back.domain.product.entity.Product;
import com.project.back.domain.product.entity.ProductFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface ProductFavoriteRepository extends JpaRepository<ProductFavorite, Long> {

    Optional<ProductFavorite> findByUserIdAndProductId(Long userId, Long productId);

    // 즐겨찾기 여부 확인
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    // 특정 사용자의 즐겨찾기 product id 목록 (목록 조회 시 즐겨찾기 여부 표시용)
    @Query("SELECT pf.product.id FROM ProductFavorite pf WHERE pf.user.id = :userId")
    Set<Long> findProductIdsByUserId(@Param("userId") Long userId);

    // 특정 제품의 즐겨찾기 모두 삭제(제품 삭제시에 필요)
    @Modifying
    @Query("DELETE FROM ProductFavorite pf WHERE pf.product.id = :productId")
    void deleteAllByProductId(@Param("productId") Long productId);

    @Query(value = "SELECT pf.product FROM ProductFavorite pf JOIN FETCH pf.product.category WHERE pf.user.id = :userId AND pf.product.isActive = true",
            countQuery = "SELECT COUNT(pf) FROM ProductFavorite pf WHERE pf.user.id = :userId AND pf.product.isActive = true")
    Page<Product> findActiveProductsByUserId(@Param("userId") Long userId, Pageable pageable);
}