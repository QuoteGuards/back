package com.project.back.domain.product.service;

import com.project.back.domain.category.entity.Category;
import com.project.back.domain.category.repository.CategoryRepository;
import com.project.back.domain.product.dto.request.ProductCreateRequest;
import com.project.back.domain.product.dto.request.ProductUpdateRequest;
import com.project.back.domain.product.dto.response.ProductResponse;
import com.project.back.domain.product.dto.response.ProductSearchResponse;
import com.project.back.domain.product.entity.Product;
import com.project.back.domain.product.repository.ProductFavoriteRepository;
import com.project.back.domain.product.repository.ProductRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductFavoriteRepository productFavoriteRepository;

    //// 최고 관리자
    // 제품 목록 조회
    public Page<ProductResponse> getProductList(Long categoryId, String keyword, Boolean isActive, Pageable pageable) {
        return productRepository.findAllWithFilters(categoryId, keyword, isActive, pageable)
                .map(ProductResponse::from);
    }

    // 제품 상세 조회
    public ProductResponse getProduct(Long productId) {
        return ProductResponse.from(findById(productId));
    }

    // 제품 등록
    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Category category = findCategory(request.getCategoryId());
        validateCode(request.getCode(), null);

        Product product = Product.builder()
                .category(category)
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .spec(request.getSpec())
                .imageUrl(request.getImageUrl())
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice())
                .unit(request.getUnit())
                .vatApplicable(request.isVatApplicable())
                .build();

        return ProductResponse.from(productRepository.save(product));
    }


    // 제품 수정
    @Transactional
    public ProductResponse update(Long productId, ProductUpdateRequest request) {
        Product product = findById(productId);
        Category category = findCategory(request.getCategoryId());
        validateCode(request.getCode(), productId);

        product.update(
                category,
                request.getName(),
                request.getCode(),
                request.getDescription(),
                request.getSpec(),
                request.getImageUrl(),
                request.getUnitPrice(),
                request.getCostPrice(),
                request.getUnit(),
                request.isVatApplicable()
        );

        return ProductResponse.from(product);
    }

    // 제품 활성화
    @Transactional
    public void activate(Long productId) {
        findById(productId).activate();
    }

    // 제품 비활성화
    @Transactional
    public void deactivate(Long productId) {
        findById(productId).deactivate();
    }

    // 제품 삭제
    @Transactional
    public void delete(Long productId) {
        Product product = findById(productId);
        // 삭제 전에 즐겨찾기도 모두 삭제
        productFavoriteRepository.deleteAllByProductId(productId);
        productRepository.delete(product);
    }

    //// 영업사원
    // 영업사원 제품 목록 조회(활성화된 제품만 검색, 즐겨찾기 표시)
    public Page<ProductSearchResponse> searchProducts(
            Long categoryId, String keyword, Long userId, Pageable pageable) {

        Set<Long> favoriteIds = productFavoriteRepository.findProductIdsByUserId(userId);

        return productRepository.findAllWithFilters(categoryId, keyword, true, pageable)
                .map(product -> ProductSearchResponse.of(
                        product,
                        favoriteIds.contains(product.getId())
                ));
    }

    // 영업사원용 제품 상세 조회 (viewCount 증가 + 즐겨찾기 여부)
    @Transactional
    public ProductSearchResponse getProductDetail(Long productId, Long userId) {
        Product product = findById(productId);

        if (!product.isActive()) {
            throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
        }


        product.increaseViewCount();

        boolean isFavorite = productFavoriteRepository.existsByUserIdAndProductId(userId, productId);
        return ProductSearchResponse.of(product, isFavorite);
    }


    private Product findById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private void validateCode(String code, Long excludeId) {
        boolean exists = (excludeId == null)
                ? productRepository.existsByCode(code)
                : productRepository.existsByCodeAndIdNot(code, excludeId);
        if (exists) {
            throw new CustomException(ErrorCode.DUPLICATE_PRODUCT_CODE);
        }
    }
}
