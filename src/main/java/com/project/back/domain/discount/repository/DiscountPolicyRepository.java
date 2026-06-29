package com.project.back.domain.discount.repository;

import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.global.enums.DiscountTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DiscountPolicyRepository extends JpaRepository<DiscountPolicy,Long> {


    // 필터 적용해서 정책 찾기 (적용대상 / 활성여부 / 정책명 키워드 / 카테고리=자손 포함)
    // 카테고리 필터: 선택 카테고리에 속하면 매칭 (max depth=3)
    //  - CATEGORY 정책: 정책 카테고리(pc)의 본인/부모/조부모가 선택 카테고리
    //  - PRODUCT 정책: 제품이 속한 카테고리(ppc)의 본인/부모/조부모가 선택 카테고리
    @Query(value = """
            SELECT p FROM DiscountPolicy p
            LEFT JOIN FETCH p.category pc
            LEFT JOIN FETCH p.product pp
            LEFT JOIN pc.parent pc1
            LEFT JOIN pc1.parent pc2
            LEFT JOIN pp.category ppc
            LEFT JOIN ppc.parent ppc1
            LEFT JOIN ppc1.parent ppc2
            WHERE (:targetType IS NULL OR p.targetType = :targetType)
              AND (:isActive IS NULL OR p.isActive = :isActive)
              AND (:keyword IS NULL OR p.policyName LIKE %:keyword%)
              AND (:categoryId IS NULL
                   OR pc.id = :categoryId OR pc1.id = :categoryId OR pc2.id = :categoryId
                   OR ppc.id = :categoryId OR ppc1.id = :categoryId OR ppc2.id = :categoryId)
            ORDER BY CASE WHEN p.targetType = 'ALL' THEN 0 ELSE 1 END, p.createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(p) FROM DiscountPolicy p
                    LEFT JOIN p.category pc
                    LEFT JOIN pc.parent pc1
                    LEFT JOIN pc1.parent pc2
                    LEFT JOIN p.product pp
                    LEFT JOIN pp.category ppc
                    LEFT JOIN ppc.parent ppc1
                    LEFT JOIN ppc1.parent ppc2
                    WHERE (:targetType IS NULL OR p.targetType = :targetType)
                      AND (:isActive IS NULL OR p.isActive = :isActive)
                      AND (:keyword IS NULL OR p.policyName LIKE %:keyword%)
                      AND (:categoryId IS NULL
                           OR pc.id = :categoryId OR pc1.id = :categoryId OR pc2.id = :categoryId
                           OR ppc.id = :categoryId OR ppc1.id = :categoryId OR ppc2.id = :categoryId)
            """)
    Page<DiscountPolicy> findAllWithFilters(
            @Param("targetType") DiscountTargetType targetType,
            @Param("isActive") Boolean isActive,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );
    //활성화된 정책 중 가장 최근 것 조회
    Optional<DiscountPolicy> findFirstByIsActiveTrueOrderByCreatedAtDesc();

    // 견적 작성 시 적용 가능한 활성 정책 후보 (PRODUCT > CATEGORY > ALL 우선은 Service에서 선택)
    @Query("""
            SELECT p FROM DiscountPolicy p
            WHERE p.isActive = true
              AND p.effectiveFrom <= CURRENT_TIMESTAMP
              AND (p.effectiveTo IS NULL OR p.effectiveTo >= CURRENT_TIMESTAMP)
              AND (
                (p.targetType = 'PRODUCT' AND p.product.id = :productId)
                OR (p.targetType = 'CATEGORY' AND p.category.id = :categoryId)
                OR p.targetType = 'ALL'
              )
            """)
    List<DiscountPolicy> findApplicableCandidates(
            @Param("productId") Long productId,
            @Param("categoryId") Long categoryId);

    // 대상별 활성 정책 1개 보장: 같은 대상(targetType + 동일 category/product)의 다른 활성 정책 일괄 비활성화
    // ALL → category/product 둘 다 null인 활성 ALL 정책, CATEGORY → 같은 category_id, PRODUCT → 같은 product_id
    @Modifying
    @Query("""
            UPDATE DiscountPolicy p SET p.isActive = false
            WHERE p.isActive = true
              AND p.id <> :excludeId
              AND p.targetType = :targetType
              AND ((:categoryId IS NULL AND p.category IS NULL) OR p.category.id = :categoryId)
              AND ((:productId IS NULL AND p.product IS NULL) OR p.product.id = :productId)
            """)
    void deactivateSameTargetActive(
            @Param("excludeId") Long excludeId,
            @Param("targetType") DiscountTargetType targetType,
            @Param("categoryId") Long categoryId,
            @Param("productId") Long productId);
}
