package com.project.back.domain.user.dto.response;

import com.project.back.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserDetailResponse {

    private final Long id;
    private final String email;
    private final String name;
    private final String department;
    private final String position;
    private final String phone;
    private final String role;
    private final String status;

    // 승인 이력
    private final Long approvedBy;
    private final LocalDateTime approvedAt;

    // 반려 이력
    private final LocalDateTime rejectedAt;
    private final String rejectReason;

    // 정지 이력
    private final Long suspendedBy;
    private final LocalDateTime suspendedAt;

    private final LocalDateTime lastLoginAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static UserDetailResponse from(User user) {
        return UserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .department(user.getDepartment())
                .position(user.getPosition())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .approvedBy(user.getApprovedBy())
                .approvedAt(user.getApprovedAt())
                .rejectedAt(user.getRejectedAt())
                .rejectReason(user.getRejectReason())
                .suspendedBy(user.getSuspendedBy())
                .suspendedAt(user.getSuspendedAt())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
