package com.project.back.domain.discount.repository;

import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.global.enums.DiscountTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscountPolicyRepository extends JpaRepository<DiscountPolicy,Long> {

    // 필터 적용해서 정책 찾기
    @Query(value = """
            SELECT p FROM DiscountPolicy p
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH p.product
            WHERE (:targetType IS NULL OR p.targetType = :targetType)
              AND (:isActive IS NULL OR p.isActive = :isActive)
            """,
            countQuery = """
            SELECT COUNT(p) FROM DiscountPolicy p
            WHERE (:targetType IS NULL OR p.targetType = :targetType)
              AND (:isActive IS NULL OR p.isActive = :isActive)
            """)
    Page<DiscountPolicy> findAllWithFilters(
            @Param("targetType") DiscountTargetType targetType,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
