package com.project.back.domain.category.repository;

import com.project.back.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    //// 카테고리 관리 화면에서

    // 카테고리 조회
    // 전체 카테고리를 한 번에 가져와서 Service에서 트리로 조립
    // (분류 클릭시마다 호출을 막기 위해)
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent ORDER BY c.depth, c.sortOrder")
    List<Category> findAllWithParent();

    // slug 중복 검사 (카테고리 식별 코드 중복 검사)
    boolean existsBySlug(String slug);

    // 수정 시 본인 제외 slug 중복 검사
    boolean existsBySlugAndIdNot(String slug, Long id);

    // 비활성화/삭제 처리 위해 자식 카테고리 로딩 (한 단계만 fetch join)
    // 두 단계 List를 동시에 fetch join하면 MultipleBagFetchException 발생.
    // 손자 카테고리는 @Transactional 안에서 child.getChildren() 지연 로딩으로 처리.
    @Query("""
        SELECT c FROM Category c
        LEFT JOIN FETCH c.children
        WHERE c.id = :id
    """)
    Optional<Category> findWithChildrenById(@Param("id") Long id);


    //// 제품 탐색 화면에서
    // 활성화된 대분류 조회
    List<Category> findAllByParentIsNullAndIsActiveTrueOrderBySortOrder();

    // 활성화된 중분류, 소분류(하위카테고리들) 조회
    List<Category> findAllByParentIdAndIsActiveTrueOrderBySortOrder(Long parentId);


    ///  이 외 필요시 아래에 추가하기

}
