package com.project.back.domain.product.repository;

import com.project.back.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 단순 crud

    // 제품 목록 조회(카테고리, 키워드, 활성화 여부 필터+페이징)
    // 카테고리는 자손 포함 매칭: 선택 카테고리가 제품의 소분류 본인/부모/조부모 중 하나면 포함
    // (카테고리 max depth=3이라 본인·부모·조부모 3단계로 전체 하위 커버)
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.category c
            WHERE (:categoryId IS NULL
                   OR c.id = :categoryId
                   OR c.parent.id = :categoryId
                   OR c.parent.parent.id = :categoryId)
              AND (:keyword IS NULL OR p.name LIKE %:keyword% OR p.code LIKE %:keyword%)
              AND (:isActive IS NULL OR p.isActive = :isActive)
            """)
    Page<Product> findAllWithFilters(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );

    // 제품 등록 시에 제품 코드 중복 검사
    boolean existsByCode(String code);

    // 수정 시에 본인을 제외한 제품 코드 중복 검사
    boolean existsByCodeAndIdNot(String code, Long id);

    // 카테고리 삭제시에 연결되어 있는 제품 수 확인
    // 카테고리 로직에서 사용함
    long countByCategoryId(Long categoryId);

    // 카테고리 목록 조회 시 각 카테고리의 제품 수 일괄 조회
    @Query("SELECT p.category.id, COUNT(p) FROM Product p GROUP BY p.category.id")
    List<Object[]> countGroupByCategoryId();

    // 카테고리 비활성화 시 해당 카테고리 자손(본인/자식/손자)에 속한 제품을 일괄 비활성화
    @Modifying
    @Query("""
            UPDATE Product p SET p.isActive = false
            WHERE p.category.id IN (
                SELECT c.id FROM Category c
                WHERE c.id = :categoryId
                   OR c.parent.id = :categoryId
                   OR c.parent.parent.id = :categoryId
            )
            """)
    void deactivateByCategorySubtree(@Param("categoryId") Long categoryId);

    // 카테고리 활성화 시 해당 카테고리 자손에 속한 제품을 일괄 활성화 (비활성화의 짝)
    @Modifying
    @Query("""
            UPDATE Product p SET p.isActive = true
            WHERE p.category.id IN (
                SELECT c.id FROM Category c
                WHERE c.id = :categoryId
                   OR c.parent.id = :categoryId
                   OR c.parent.parent.id = :categoryId
            )
            """)
    void activateByCategorySubtree(@Param("categoryId") Long categoryId);

}
