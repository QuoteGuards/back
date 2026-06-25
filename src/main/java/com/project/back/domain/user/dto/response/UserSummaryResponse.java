package com.project.back.domain.user.dto.response;

import com.project.back.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserSummaryResponse {

    private final Long id;
    private final String memberNumber;
    private final String email;
    private final String name;
    private final String department;
    private final String position;
    private final String phone;
    private final String role;
    private final String status;
    private final boolean mustChangePassword;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static UserSummaryResponse from(User user) {
        return UserSummaryResponse.builder()
                .id(user.getId())
                .memberNumber(user.getMemberNumber())
                .email(user.getEmail())
                .name(user.getName())
                .department(user.getDepartment())
                .position(user.getPosition())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .mustChangePassword(user.isMustChangePassword())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
