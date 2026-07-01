package com.project.back.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class LoginResponse {

    private final String tokenType = "Bearer";
    private final String accessToken;
    private final String refreshToken;

    private LoginResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public static LoginResponse of(String accessToken, String refreshToken) {
        return new LoginResponse(accessToken, refreshToken);
    }
}
