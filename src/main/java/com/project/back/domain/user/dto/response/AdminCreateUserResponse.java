package com.project.back.domain.user.dto.response;

import com.project.back.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 관리자가 신규 계정을 생성했을 때 반환하는 응답 DTO.
 * temporaryPassword는 최초 1회 전달용이며, DB에는 암호화 후 저장되고 로그에 출력하지 않는다.
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

    /** 관리자에게 최초 1회만 전달. 화면에서 안내 후 폐기. */
    private final String temporaryPassword;

    private final LocalDateTime createdAt;

    public static AdminCreateUserResponse from(User user, String temporaryPassword) {
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
                .temporaryPassword(temporaryPassword)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
