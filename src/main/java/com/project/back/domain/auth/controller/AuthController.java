package com.project.back.domain.auth.controller;

import com.project.back.domain.auth.dto.request.LoginRequest;
import com.project.back.domain.auth.dto.request.PasswordResetConfirmRequest;
import com.project.back.domain.auth.dto.request.PasswordResetRequest;
import com.project.back.domain.auth.dto.request.RefreshTokenRequest;
import com.project.back.domain.auth.dto.request.SetInitialPasswordRequest;
import com.project.back.domain.auth.dto.response.LoginResponse;
import com.project.back.domain.auth.dto.response.TokenRefreshResponse;
import com.project.back.domain.auth.ratelimit.PasswordResetRateLimiter;
import com.project.back.domain.auth.service.AuthService;
import com.project.back.domain.auth.service.InitialPasswordSetupService;
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
    private final InitialPasswordSetupService initialPasswordSetupService;

    @Value("${app.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, ipAddress, userAgent);
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
     * 초기 비밀번호 설정 (비인증 공개 API)
     *
     * <p>관리자가 계정 생성 후 이메일로 발송된 링크에서 호출된다.
     * 토큰 purpose가 INITIAL_PASSWORD_SETUP인 경우에만 처리한다.</p>
     */
    @PostMapping("/set-initial-password")
    public ResponseEntity<ApiResponse<Void>> setInitialPassword(
            @Valid @RequestBody SetInitialPasswordRequest request
    ) {
        initialPasswordSetupService.setInitialPassword(
                request.getToken(), request.getNewPassword(), request.getNewPasswordConfirm());
        return ResponseEntity.ok(ApiResponse.success("비밀번호 설정이 완료되었습니다. 로그인해주세요.", null));
    }

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
