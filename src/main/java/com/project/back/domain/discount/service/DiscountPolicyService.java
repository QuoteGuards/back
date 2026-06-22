package com.project.back.domain.discount.service;

import com.project.back.domain.category.entity.Category;
import com.project.back.domain.category.repository.CategoryRepository;
import com.project.back.domain.discount.dto.request.DiscountPolicyCreateRequest;
import com.project.back.domain.discount.dto.request.DiscountPolicyUpdateRequest;
import com.project.back.domain.discount.dto.response.DiscountPolicyResponse;
import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.domain.discount.repository.DiscountPolicyRepository;
import com.project.back.domain.product.entity.Product;
import com.project.back.domain.product.repository.ProductRepository;
import com.project.back.global.enums.DiscountTargetType;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscountPolicyService {

    private final DiscountPolicyRepository discountPolicyRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // 목록 조회 (targetType / isActive 필터 + 페이징)
    public Page<DiscountPolicyResponse> getList(DiscountTargetType targetType, Boolean isActive, Pageable pageable) {
        return discountPolicyRepository.findAllWithFilters(targetType, isActive, pageable)
                .map(DiscountPolicyResponse::from);
    }

    // 상세 조회
    public DiscountPolicyResponse get(Long policyId) {
        return DiscountPolicyResponse.from(findById(policyId));
    }

    // 등록
    @Transactional
    public DiscountPolicyResponse create(DiscountPolicyCreateRequest request, Long userId) {
        Category category = resolveCategory(request.getTargetType(), request.getCategoryId());
        Product product = resolveProduct(request.getTargetType(), request.getProductId());
        validatePeriod(request.getEffectiveFrom(), request.getEffectiveTo());

        LocalDateTime from = request.getEffectiveFrom() != null
                ? request.getEffectiveFrom() : LocalDateTime.now();

        DiscountPolicy policy = DiscountPolicy.builder()
                .policyName(request.getName())
                .targetType(request.getTargetType())
                .category(category)
                .product(product)
                .maxDiscountRate(request.getMaxDiscountRate())
                .minProfitRate(request.getMinProfitRate())
                .approvalThresholdAmount(request.getHighAmountThreshold())
                .effectiveFrom(from)
                .effectiveTo(request.getEffectiveTo())
                .createdBy(userId)
                .build();

        return DiscountPolicyResponse.from(discountPolicyRepository.save(policy));
    }

    // 수정
    @Transactional
    public DiscountPolicyResponse update(Long policyId, DiscountPolicyUpdateRequest request) {
        DiscountPolicy policy = findById(policyId);
        Category category = resolveCategory(request.getTargetType(), request.getCategoryId());
        Product product = resolveProduct(request.getTargetType(), request.getProductId());
        validatePeriod(request.getEffectiveFrom(), request.getEffectiveTo());

        LocalDateTime from = request.getEffectiveFrom() != null
                ? request.getEffectiveFrom() : policy.getEffectiveFrom();

        policy.update(
                request.getName(),
                request.getTargetType(),
                category,
                product,
                request.getMaxDiscountRate(),
                request.getMinProfitRate(),
                request.getHighAmountThreshold(),
                from,
                request.getEffectiveTo()
        );
        return DiscountPolicyResponse.from(policy);
    }

    @Transactional
    public void activate(Long policyId) { findById(policyId).activate(); }

    @Transactional
    public void deactivate(Long policyId) { findById(policyId).deactivate(); }

    @Transactional
    public void delete(Long policyId) {
        discountPolicyRepository.delete(findById(policyId));
    }

    private DiscountPolicy findById(Long policyId) {
        return discountPolicyRepository.findById(policyId)
                .orElseThrow(() -> new CustomException(ErrorCode.DISCOUNT_POLICY_NOT_FOUND));
    }

    // targetType=CATEGORY 일 때만 카테고리 적용, 그 외엔 null
    private Category resolveCategory(DiscountTargetType targetType, Long categoryId) {
        if (targetType != DiscountTargetType.CATEGORY) return null;
        if (categoryId == null) throw new CustomException(ErrorCode.DISCOUNT_TARGET_REQUIRED);
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    // targetType=PRODUCT 일 때만 제품 적용, 그 외엔 null
    private Product resolveProduct(DiscountTargetType targetType, Long productId) {
        if (targetType != DiscountTargetType.PRODUCT) return null;
        if (productId == null) throw new CustomException(ErrorCode.DISCOUNT_TARGET_REQUIRED);
        return productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new CustomException(ErrorCode.DISCOUNT_INVALID_PERIOD);
        }
    }
}