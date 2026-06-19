package com.project.back.domain.user.controller;

import com.project.back.domain.user.dto.response.UserStatsResponse;
import com.project.back.domain.user.service.UserStatsService;
import com.project.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserStatsController {

    private final UserStatsService userStatsService;

    /**
     * 내 통계 조회
     * SALES_STAFF, SALES_MANAGER, SUPER_ADMIN 모두 본인 통계 조회 가능
     */
    @GetMapping("/api/users/me/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserStatsResponse>> getMyStats(
            @AuthenticationPrincipal Long userId
    ) {
        UserStatsResponse response = userStatsService.getMyStats(userId);
        return ResponseEntity.ok(ApiResponse.success("내 통계 조회 성공", response));
    }

    /**
     * 관리자 사용자별 통계 조회
     * SUPER_ADMIN, SALES_MANAGER만 접근 가능
     */
    @GetMapping("/api/admin/users/{userId}/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SALES_MANAGER')")
    public ResponseEntity<ApiResponse<UserStatsResponse>> getUserStats(
            @PathVariable Long userId
    ) {
        UserStatsResponse response = userStatsService.getUserStats(userId);
        return ResponseEntity.ok(ApiResponse.success("사용자 통계 조회 성공", response));
    }
}
