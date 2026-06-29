package com.project.back.domain.user.controller;

import com.project.back.domain.auth.service.InitialPasswordSetupService;
import com.project.back.domain.user.dto.request.AdminCreateUserRequest;
import com.project.back.domain.user.dto.request.ChangeUserRoleRequest;
import com.project.back.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.back.domain.user.dto.response.AdminCreateUserResponse;
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
import org.springframework.http.HttpStatus;
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
    private final InitialPasswordSetupService initialPasswordSetupService;

    /**
     * 관리자가 신규 사원 계정을 직접 생성
     * - 회원번호로 이메일 자동 생성 ({memberNumber}@domain)
     * - 임시 비밀번호 없음: 등록된 이메일로 초기 비밀번호 설정 링크 발송
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminCreateUserResponse>> createUser(
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        AdminCreateUserResponse result = userManagementService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("사용자 계정이 생성되었습니다. 등록된 이메일로 초기 비밀번호 설정 링크 발송을 요청했습니다.", result));
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
     * 사용자 정보 수정 (이름, 부서, 직급, 전화번호)
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
     * 사용자 권한 변경
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
     * 사용자 비활성화 ACTIVE → SUSPENDED
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
     * 사용자 재활성화 SUSPENDED → ACTIVE
     */
    @PatchMapping("/{userId}/reactivate")
    public ResponseEntity<ApiResponse<UserDetailResponse>> reactivateUser(@PathVariable Long userId) {
        UserDetailResponse result = userManagementService.reactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success("사용자가 재활성화되었습니다.", result));
    }

    /**
     * 사용자 삭제 (소프트 삭제)
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long userId
    ) {
        userManagementService.deleteUser(requesterId, userId);
        return ResponseEntity.ok(ApiResponse.success("사용자가 삭제되었습니다.", null));
    }

    /**
     * 초기 비밀번호 설정 링크 재발송
     * - 비밀번호 설정이 완료되지 않은 사용자에게만 발송 가능
     * - 60초 쿨다운 적용
     */
    @PostMapping("/{userId}/initial-password/resend")
    public ResponseEntity<ApiResponse<Void>> resendInitialPasswordSetupLink(
            @PathVariable Long userId
    ) {
        initialPasswordSetupService.resendSetupLink(userId);
        return ResponseEntity.ok(ApiResponse.success("초기 비밀번호 설정 링크 재발송을 요청했습니다.", null));
    }
}
