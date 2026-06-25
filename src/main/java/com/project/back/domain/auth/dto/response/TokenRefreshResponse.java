package com.project.back.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class TokenRefreshResponse {

    private final String tokenType = "Bearer";
    private final String accessToken;

    private TokenRefreshResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public static TokenRefreshResponse of(String accessToken) {
        return new TokenRefreshResponse(accessToken);
    }
}
