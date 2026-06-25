package com.project.back.global.security;

import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * [초기 비밀번호 강제 변경 필터]
 *
 * <p>JWT 인증 필터 이후 실행된다. {@link SecurityConfig}에서 직접 생성하여 등록한다.</p>
 * <p>{@code @Component}를 붙이지 않아 {@code @WebMvcTest}의 Filter 자동 스캔 대상에서 제외된다.</p>
 *
 * <ul>
 *   <li>OPTIONS 요청 → 통과 (CORS preflight)</li>
 *   <li>미인증 요청 → 통과 (Spring Security가 401 처리)</li>
 *   <li>PATCH /api/users/me/password → 통과 (비밀번호 변경 자체는 허용)</li>
 *   <li>mustChangePassword=true인 인증된 사용자의 그 외 요청 → 403</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/api/users/me/password";
    private static final String LOGOUT_PATH = "/api/auth/logout";

    private final UserRepository userRepository;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // OPTIONS 요청 통과 (CORS preflight)
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 비밀번호 변경 엔드포인트 통과
        if (HttpMethod.PATCH.matches(request.getMethod())
                && CHANGE_PASSWORD_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 로그아웃 엔드포인트 통과 (초기 비밀번호 변경 전에도 로그아웃 허용)
        if (HttpMethod.POST.matches(request.getMethod())
                && LOGOUT_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 미인증 요청 통과 (JWT 필터에서 인증 정보 없음 → Spring Security가 처리)
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        // DB 조회 후 mustChangePassword 확인
        // 사용자를 찾을 수 없는 경우 인증 불가 상태로 간주하여 차단
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("MustChangePasswordFilter: userId={} not found in DB — blocking request", userId);
            securityErrorResponseWriter.write(response, ErrorCode.MUST_CHANGE_PASSWORD);
            return;
        }

        if (userOpt.get().isMustChangePassword()) {
            log.debug("MustChangePasswordFilter: userId={} blocked - mustChangePassword=true", userId);
            securityErrorResponseWriter.write(response, ErrorCode.MUST_CHANGE_PASSWORD);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
