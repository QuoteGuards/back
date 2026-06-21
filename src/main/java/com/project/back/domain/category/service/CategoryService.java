package com.project.back.domain.category.service;

import com.project.back.domain.category.dto.request.CategoryCreateRequest;
import com.project.back.domain.category.dto.request.CategoryUpdateRequest;
import com.project.back.domain.category.dto.response.CategoryResponse;
import com.project.back.domain.category.dto.response.CategoryTreeResponse;
import com.project.back.domain.category.entity.Category;
import com.project.back.domain.category.repository.CategoryRepository;
import com.project.back.domain.product.repository.ProductRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // 카테고리 깊이 최대 3(1=대분류,2=중분류,3=소분류)
    private static final int MAX_DEPTH=3;

    //// 카테고리 crud
    // 전체 분류 트리로 조회(비활성화 포함)
    @Transactional(readOnly=true)
    public List<CategoryTreeResponse> getCategoryList(){
        List<Category> all = categoryRepository.findAllWithParent();
        return buildTree(all);
    }


    // 특정 부모 분류의 활성된 자식들 목록(드릴다운)
    @Transactional(readOnly=true)
    public List<CategoryResponse> getActiveChildren(Long parentId){
        List<Category> children = (parentId == null)
                ? categoryRepository.findAllByParentIsNullAndIsActiveTrueOrderBySortOrder()
                : categoryRepository.findAllByParentIdAndIsActiveTrueOrderBySortOrder(parentId);

        return children.stream()
                .map(CategoryResponse::from)
                .toList();
    }

    // 등록
    @Transactional
    public CategoryResponse create(CategoryCreateRequest request){
        Category parent = resolveParent(request.getParentId());
        int depth = (parent==null)?1:parent.getDepth()+1;

        // 분류, 카테코리 식별번호 검증
        validateDepth(depth);
        validateSlug(request.getSlug(), null);

        Category category = Category.builder()
                .parent(parent)
                .name(request.getName())
                .slug(request.getSlug())
                .depth(depth)
                .sortOrder(request.getSortOrder())
                .isActive(true)
                .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    // 수정
    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = findById(id);
        validateSlug(request.getSlug(), id);

        category.update(request.getName(), request.getSlug(), request.getSortOrder());

        return CategoryResponse.from(category);
    }

    // 삭제
    @Transactional
    public void delete(Long id) {
        Category category = findById(id);

        // 연결된 제품이 있으면 삭제 불가
        long productCount = productRepository.countByCategoryId(id);
        // 수정, max_depth는 카테고리 분류 확인할때 사용
        if (productCount > 0) {
            throw new CustomException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        // 하위 카테고리에 연결된 제품도 확인
        boolean childHasProducts = category.getChildren().stream()
                .anyMatch(child -> productRepository.countByCategoryId(child.getId()) > 0);
        if (childHasProducts) {
            throw new CustomException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        categoryRepository.delete(category);
        // DB ON DELETE CASCADE로 자식 카테고리도 함께 삭제됨
    }

    //// 카테고리 활성화/비활성화
    // 활성화
    @Transactional
    public void activate(Long id) {
        Category category = findById(id);
        category.activate();

        // 부모 체인 활성화 (최대 depth=3이므로 최대 2번 LAZY 로딩)
        Category parent = category.getParent();
        while (parent != null) {
            parent.activate();
            parent = parent.getParent();
        }
    }

    // 비활성화
    @Transactional
    public void deactivate(Long id) {
        Category category = categoryRepository.findWithChildrenById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        category.deactivate();
        category.getChildren().forEach(child -> {
            child.deactivate();
            child.getChildren().forEach(Category::deactivate);
        });
    }



    // 카테고리 등록에서 부모 분류 찾는 메서드
    private Category resolveParent(Long parentId) {
        if (parentId == null)
            return null;
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    // 분류 검증
    private void validateDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new CustomException(ErrorCode.CATEGORY_MAX_DEPTH_EXCEEDED);
        }
    }

    // 카테고리 식별 번호 중복 여부 확인
    private void validateSlug(String slug, Long excludeId) {
        boolean exists = (excludeId == null)
                ? categoryRepository.existsBySlug(slug) // 등록시
                : categoryRepository.existsBySlugAndIdNot(slug, excludeId); // 수정시
        if (exists) {
            throw new CustomException(ErrorCode.DUPLICATE_SLUG);
        }
    }

    private Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }



    // 조회에서 트리구조로 만드는 메서드
    private List<CategoryTreeResponse> buildTree(List<Category> all) {
        Map<Long, CategoryTreeResponse> map = new LinkedHashMap<>();
        List<CategoryTreeResponse> roots = new ArrayList<>();

        for (Category c : all) {
            CategoryTreeResponse dto = CategoryTreeResponse.from(c);
            map.put(c.getId(), dto);

            if (c.getParent() == null) {
                roots.add(dto);
            } else {
                map.get(c.getParent().getId()).addChild(dto);
            }
        }
        return roots;
    }

}
