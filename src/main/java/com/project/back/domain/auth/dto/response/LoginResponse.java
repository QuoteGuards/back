package com.project.back.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class LoginResponse {

    private final String tokenType = "Bearer";
    private final String accessToken;

    private LoginResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public static LoginResponse of(String accessToken) {
        return new LoginResponse(accessToken);
    }
}
