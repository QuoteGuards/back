package com.project.back.domain.discount.controller;

import com.project.back.domain.discount.dto.request.DiscountPolicyCreateRequest;
import com.project.back.domain.discount.dto.request.DiscountPolicyUpdateRequest;
import com.project.back.domain.discount.dto.response.DiscountPolicyResponse;
import com.project.back.domain.discount.service.DiscountPolicyService;
import com.project.back.global.common.ApiResponse;
import com.project.back.global.enums.DiscountTargetType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 관리자의 할인정책 관리
@RestController
@RequestMapping("/api/admin/discounts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminDiscountController {

    private final DiscountPolicyService discountPolicyService;

    // 할인정책목록조회
    @GetMapping
    public ResponseEntity<ApiResponse<Page<DiscountPolicyResponse>>> getList(
            @RequestParam(required = false) DiscountTargetType targetType,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "할인 정책 목록 조회 성공",
                discountPolicyService.getList(targetType, isActive, pageable)
        ));
    }

    // 할인정책상세조회
    @GetMapping("/{policyId}")
    public ResponseEntity<ApiResponse<DiscountPolicyResponse>> get(@PathVariable Long policyId) {
        return ResponseEntity.ok(ApiResponse.success(
                "할인 정책 상세 조회 성공",
                discountPolicyService.get(policyId)
        ));
    }

    // 할인정책등록
    @PostMapping
    public ResponseEntity<ApiResponse<DiscountPolicyResponse>> create(
            @RequestBody @Valid DiscountPolicyCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "할인 정책 등록 성공",
                discountPolicyService.create(request, userId)
        ));
    }

    // 할인정책수정
    @PatchMapping("/{policyId}")
    public ResponseEntity<ApiResponse<DiscountPolicyResponse>> update(
            @PathVariable Long policyId,
            @RequestBody @Valid DiscountPolicyUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "할인 정책 수정 성공",
                discountPolicyService.update(policyId, request)
        ));
    }

    // 할인정책활성화
    @PatchMapping("/{policyId}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long policyId) {
        discountPolicyService.activate(policyId);
        return ResponseEntity.ok(ApiResponse.success("할인 정책 활성화 성공", null));
    }

    // 비활성화
    @PatchMapping("/{policyId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long policyId) {
        discountPolicyService.deactivate(policyId);
        return ResponseEntity.ok(ApiResponse.success("할인 정책 비활성화 성공", null));
    }

    // 삭제
    @DeleteMapping("/{policyId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long policyId) {
        discountPolicyService.delete(policyId);
        return ResponseEntity.ok(ApiResponse.success("할인 정책 삭제 성공", null));
    }
}