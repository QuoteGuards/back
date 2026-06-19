package com.project.back.global.security;

import com.project.back.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * [JWT 인가 예외 처리기]
 * 인증은 성공했으나 해당 리소스에 대한 권한이 없는 사용자가 접근할 때 호출됩니다.
 * (예: SALES_STAFF 권한으로 SUPER_ADMIN 전용 API 접근 시)
 * 403 Forbidden 응답을 공통 ApiResponse 형식의 JSON으로 반환합니다.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    @Override
    public void handle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AccessDeniedException accessDeniedException
    ) throws IOException {
        securityErrorResponseWriter.write(response, ErrorCode.ACCESS_DENIED);
    }
}
