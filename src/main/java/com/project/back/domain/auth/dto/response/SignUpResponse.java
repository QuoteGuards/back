package com.project.back.domain.auth.dto.response;

import com.project.back.domain.user.entity.User;
import lombok.Getter;

@Getter
public class SignUpResponse {

    private final Long userId;
    private final String email;
    private final String name;
    private final String role;
    private final String status;

    private SignUpResponse(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.role = user.getRole().name();
        this.status = user.getStatus().name();
    }

    public static SignUpResponse from(User user) {
        return new SignUpResponse(user);
    }
}
