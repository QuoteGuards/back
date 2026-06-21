package com.project.back.domain.product.repository;

import com.project.back.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 단순 crud

    // 제품 목록 조회(카테고릴, 키워드, 활성화 여부 필터+페이징)
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.category c
            WHERE (:categoryId IS NULL OR c.id = :categoryId)
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

}
