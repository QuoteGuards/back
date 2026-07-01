package com.project.back.domain.user.dto.response;

import com.project.back.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserDetailResponse {

    private final Long id;
    private final String memberNumber;
    private final String email;
    private final String name;
    private final String department;
    private final String position;
    private final String phone;
    private final String role;
    private final String status;
    private final boolean passwordInitialized;
    private final boolean mustChangePassword;

    // 계정 생성자 정보
    private final Long createdBy;
    private final String createdByName;
    private final String createdByMemberNumber;

    // 정지 정보
    private final Long suspendedBy;
    private final LocalDateTime suspendedAt;

    // 삭제 일시
    private final LocalDateTime deletedAt;

    // 비밀번호 변경 일시
    private final LocalDateTime passwordChangedAt;

    private final LocalDateTime lastLoginAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static UserDetailResponse from(User user) {
        return from(user, null, null);
    }

    public static UserDetailResponse from(User user, String createdByName, String createdByMemberNumber) {
        return UserDetailResponse.builder()
                .id(user.getId())
                .memberNumber(user.getMemberNumber())
                .email(user.getEmail())
                .name(user.getName())
                .department(user.getDepartment())
                .position(user.getPosition())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .passwordInitialized(user.isPasswordInitialized())
                .mustChangePassword(user.isMustChangePassword())
                .createdBy(user.getCreatedBy())
                .createdByName(createdByName)
                .createdByMemberNumber(createdByMemberNumber)
                .suspendedBy(user.getSuspendedBy())
                .suspendedAt(user.getSuspendedAt())
                .deletedAt(user.getDeletedAt())
                .passwordChangedAt(user.getPasswordChangedAt())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
