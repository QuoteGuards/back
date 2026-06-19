package com.project.back.global.security;

import com.project.back.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * [JWT 인증 예외 처리기]
 * 인증되지 않은 사용자(토큰이 없거나, 만료되었거나, 위조된 경우)가
 * 인증이 필요한 보호된 API 리소스에 접근했을 때 시큐리티에 의해 자동으로 호출됩니다.
 * 필터(Filter) 영역에서 발생한 인증 예외를 커스텀 에러 규격(ApiResponse)인 JSON 형태로 변환하여 클라이언트에 반환합니다.
 */

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    // 토큰 검증에 실패하거나 토큰 없이 보호된 API에 접근하면 스프링 시큐리티가 이 commence 메서드를 강제로 호출
    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException
    ) throws IOException {
        securityErrorResponseWriter.write(response, ErrorCode.INVALID_TOKEN);
    }
}
