package com.project.back.domain.user.controller;

import com.project.back.domain.user.dto.request.ChangeUserRoleRequest;
import com.project.back.domain.user.dto.request.RejectUserRequest;
import com.project.back.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.back.domain.user.dto.response.UserDetailResponse;
import com.project.back.domain.user.dto.response.UserSummaryResponse;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.service.UserManagementService;
import com.project.back.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final UserManagementService userManagementService;

    /**
     * 승인 대기 사용자 목록 조회
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> getPendingUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<UserSummaryResponse> result = userManagementService.getPendingUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success("승인 대기 사용자 목록 조회 성공", result));
    }

    /**
     * 전체 사용자 목록 조회 (role, status, keyword 검색 + 페이징)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> getAllUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<UserSummaryResponse> result = userManagementService.getAllUsers(role, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("사용자 목록 조회 성공", result));
    }

    /**
     * 사용자 상세 조회
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(@PathVariable Long userId) {
        UserDetailResponse result = userManagementService.getUserDetail(userId);
        return ResponseEntity.ok(ApiResponse.success("사용자 상세 조회 성공", result));
    }

    /**
     * 가입 승인 (PENDING → APPROVED) - approved_by, approved_at 기록
     */
    @PatchMapping("/{userId}/approve")
    public ResponseEntity<ApiResponse<UserDetailResponse>> approveUser(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long userId
    ) {
        UserDetailResponse result = userManagementService.approveUser(requesterId, userId);
        return ResponseEntity.ok(ApiResponse.success("사용자 승인이 완료되었습니다.", result));
    }

    /**
     * 가입 반려 (PENDING → REJECTED) - rejected_at, reject_reason 기록
     */
    @PatchMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<UserDetailResponse>> rejectUser(
            @PathVariable Long userId,
            @Valid @RequestBody RejectUserRequest request
    ) {
        UserDetailResponse result = userManagementService.rejectUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success("사용자 반려가 완료되었습니다.", result));
    }

    /**
     * 사용자 정보 수정 (이름, 전화번호)
     */
    @PatchMapping("/{userId}/info")
    public ResponseEntity<ApiResponse<UserDetailResponse>> updateUserInfo(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserInfoRequest request
    ) {
        UserDetailResponse result = userManagementService.updateUserInfo(userId, request);
        return ResponseEntity.ok(ApiResponse.success("사용자 정보가 수정되었습니다.", result));
    }

    /**
     * 사용자 권한 변경 (자기 자신 변경 불가)
     */
    @PatchMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<UserDetailResponse>> changeUserRole(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeUserRoleRequest request
    ) {
        UserDetailResponse result = userManagementService.changeUserRole(requesterId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("사용자 권한이 변경되었습니다.", result));
    }

    /**
     * 사용자 비활성화 APPROVED → SUSPENDED (자기 자신 비활성화 불가)
     */
    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<ApiResponse<UserDetailResponse>> suspendUser(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long userId
    ) {
        UserDetailResponse result = userManagementService.suspendUser(requesterId, userId);
        return ResponseEntity.ok(ApiResponse.success("사용자가 비활성화되었습니다.", result));
    }

    /**
     * 사용자 재활성화 SUSPENDED → APPROVED
     */
    @PatchMapping("/{userId}/reactivate")
    public ResponseEntity<ApiResponse<UserDetailResponse>> reactivateUser(@PathVariable Long userId) {
        UserDetailResponse result = userManagementService.reactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success("사용자가 재활성화되었습니다.", result));
    }
}
