package com.project.back.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class LoginResponse {

    private final String tokenType = "Bearer";
    private final String accessToken;
    private final boolean mustChangePassword;

    private LoginResponse(String accessToken, boolean mustChangePassword) {
        this.accessToken = accessToken;
        this.mustChangePassword = mustChangePassword;
    }

    public static LoginResponse of(String accessToken, boolean mustChangePassword) {
        return new LoginResponse(accessToken, mustChangePassword);
    }
}
