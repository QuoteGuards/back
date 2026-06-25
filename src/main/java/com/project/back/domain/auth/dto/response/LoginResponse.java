package com.project.back.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class LoginResponse {

    private final String tokenType = "Bearer";
    private final String accessToken;
    private final String refreshToken;
    private final boolean mustChangePassword;

    private LoginResponse(String accessToken, String refreshToken, boolean mustChangePassword) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.mustChangePassword = mustChangePassword;
    }

    public static LoginResponse of(String accessToken, String refreshToken, boolean mustChangePassword) {
        return new LoginResponse(accessToken, refreshToken, mustChangePassword);
    }
}
