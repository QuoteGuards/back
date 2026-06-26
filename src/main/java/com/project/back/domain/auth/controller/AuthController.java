package com.project.back.domain.auth.controller;

import com.project.back.domain.auth.dto.request.LoginRequest;
import com.project.back.domain.auth.dto.request.PasswordResetConfirmRequest;
import com.project.back.domain.auth.dto.request.PasswordResetRequest;
import com.project.back.domain.auth.dto.request.RefreshTokenRequest;
import com.project.back.domain.auth.dto.response.LoginResponse;
import com.project.back.domain.auth.dto.response.TokenRefreshResponse;
import com.project.back.domain.auth.ratelimit.PasswordResetRateLimiter;
import com.project.back.domain.auth.service.AuthService;
import com.project.back.domain.auth.service.PasswordResetService;
import com.project.back.global.common.ApiResponse;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final PasswordResetRateLimiter passwordResetRateLimiter;

    /**
     * 신뢰할 수 있는 리버스 프록시(nginx, ALB 등) 뒤에 배포된 경우에만 {@code true}로 설정한다.
     * {@code false}(기본값)이면 X-Forwarded-For/X-Real-IP를 무시하고 RemoteAddr만 사용한다.
     */
    @Value("${app.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인에 성공했습니다.", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("액세스 토큰이 재발급되었습니다.", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal Long userId
    ) {
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다.", null));
    }

    /**
     * 비밀번호 재설정 링크 요청 (비인증 공개 API)
     *
     * <p>이메일 존재 여부와 무관하게 동일한 응답을 반환한다. (계정 열거 방어)</p>
     * <p>IP+email 기준 60초 쿨다운으로 메일 폭주 및 토큰 churn을 방지한다.</p>
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        if (!passwordResetRateLimiter.tryAcquire(clientIp, request.getEmail())) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOO_MANY_REQUESTS);
        }

        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "입력한 이메일이 등록되어 있다면 비밀번호 재설정 안내가 발송됩니다.", null));
    }

    /**
     * 비밀번호 재설정 확인 (비인증 공개 API)
     */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 성공적으로 변경되었습니다.", null));
    }

    /**
     * 실제 클라이언트 IP를 추출한다.
     *
     * <p>{@code app.trust-proxy-headers=true}일 때만 {@code X-Forwarded-For} / {@code X-Real-IP}를 신뢰한다.
     * 이 설정은 신뢰할 수 있는 리버스 프록시(nginx, ALB 등) 뒤에 배포된 환경에서만 활성화해야 한다.
     * 그 외 환경에서는 클라이언트가 헤더를 조작하여 Rate Limiter를 우회할 수 있으므로
     * {@code request.getRemoteAddr()}만 사용한다.</p>
     */
    private String extractClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                return xff.split(",")[0].trim();
            }
            String xri = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(xri)) {
                return xri.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
