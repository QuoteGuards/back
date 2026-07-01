package com.project.back.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * [JWT 인증 필터]
 *
 * <ul>
 *   <li>토큰 없음 → 필터 체인 통과 (Spring Security가 인증 필요 여부 판단)</li>
 *   <li>토큰 유효 → SecurityContext에 Authentication 저장 후 통과</li>
 *   <li>토큰 존재하지만 유효하지 않음 → 즉시 401 반환</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        // 토큰 없음: 인증 없이 체인 통과 → Spring Security가 권한 검사
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!jwtTokenProvider.validateToken(token)) {
                // 토큰 존재하지만 유효하지 않음 → 즉시 401 반환
                SecurityContextHolder.clearContext();
                jwtAuthenticationEntryPoint.commence(
                        request,
                        response,
                        new BadCredentialsException("유효하지 않은 토큰입니다.")
                );
                return;
            }

            String subject = jwtTokenProvider.getSubject(token);
            Long userId = Long.parseLong(subject);
            String role = jwtTokenProvider.getRole(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            // 토큰 파싱/검증 중 예외 → 401 반환
            log.warn("JWT authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            jwtAuthenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("유효하지 않은 토큰입니다.", e)
            );
            return;
        }

        // 인증 성공 후 필터 체인 통과 (try 외부 → 이후 필터 예외가 JWT 오류로 오인되지 않음)
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        // SSE 구독은 장기 JWT를 URL에 노출하지 않고 별도의 단기 SSE 토큰으로 인증한다.
        // (NotificationController.subscribe + SseTokenService 참조)
        return null;
    }
}
