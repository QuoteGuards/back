package com.project.back.global.security;

import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // 512-bit Base64 secret (test용)
    private static final String SECRET =
            "dGhpcyBpcyBhIHZlcnkgbG9uZyBzZWNyZXQga2V5IGZvciBKV1QgdG9rZW4gZ2VuZXJhdGlvbiBpbiBRdW90ZUd1YXJk";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3600L);
    }

    @Test
    @DisplayName("토큰 생성 및 subject 추출 성공")
    void createAndParseToken() {
        String token = jwtTokenProvider.createAccessToken(1L, "user@example.com", "SALES_STAFF");

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.getSubject(token)).isEqualTo("1");
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo("SALES_STAFF");
    }

    @Test
    @DisplayName("유효하지 않은 토큰 - INVALID_TOKEN 예외")
    void parseInvalidToken() {
        assertThatThrownBy(() -> jwtTokenProvider.parseToken("invalid.token.value"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("만료된 토큰 - EXPIRED_TOKEN 예외")
    void parseExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1L); // 과거 만료
        String token = expiredProvider.createAccessToken(1L, "user@example.com", "SALES_STAFF");

        assertThatThrownBy(() -> expiredProvider.parseToken(token))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXPIRED_TOKEN));
    }

    @Test
    @DisplayName("validateToken - 유효한 토큰은 true 반환")
    void validateToken_valid() {
        String token = jwtTokenProvider.createAccessToken(1L, "user@example.com", "SALES_STAFF");
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken - 유효하지 않은 토큰은 false 반환")
    void validateToken_invalid() {
        assertThat(jwtTokenProvider.validateToken("bad.token")).isFalse();
    }
}
