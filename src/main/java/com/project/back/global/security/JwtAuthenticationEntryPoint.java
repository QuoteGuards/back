package com.project.back.global.security;

import tools.jackson.databind.ObjectMapper;
import com.project.back.global.common.ApiResponse;
import com.project.back.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * [JWT 인증 예외 처리기]
 * 인증되지 않은 사용자(토큰이 없거나, 만료되었거나, 위조된 경우)가
 * 인증이 필요한 보호된 API 리소스에 접근했을 때 시큐리티에 의해 자동으로 호출됩니다.
 * 필터(Filter) 영역에서 발생한 인증 예외를 커스텀 에러 규격(ApiResponse)인 JSON 형태로 변환하여 클라이언트에 반환합니다.
 */

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(   // 토큰 검증에 실패하거나 토큰 없이 보호된 API에 접근하면 스프링 시큐리티가 이 commence 메서드를 강제로 호출
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        ErrorCode errorCode = ErrorCode.INVALID_TOKEN;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> apiResponse = ApiResponse.fail(errorCode.getCode(), errorCode.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
