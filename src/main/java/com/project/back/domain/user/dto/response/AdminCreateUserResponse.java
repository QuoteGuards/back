package com.project.back.domain.user.dto.response;

import com.project.back.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 관리자가 신규 계정을 생성했을 때 반환하는 응답 DTO.
 * 임시 비밀번호는 포함하지 않는다. 사용자에게 이메일로 초기 비밀번호 설정 링크를 발송한다.
 */
@Getter
@Builder
public class AdminCreateUserResponse {

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
    private final LocalDateTime createdAt;

    public static AdminCreateUserResponse from(User user) {
        return AdminCreateUserResponse.builder()
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
                .createdAt(user.getCreatedAt())
                .build();
    }
}
