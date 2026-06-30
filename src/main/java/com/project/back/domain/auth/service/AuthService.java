package com.project.back.domain.auth.service;

import com.project.back.domain.auth.dto.request.LoginRequest;
import com.project.back.domain.auth.dto.request.RefreshTokenRequest;
import com.project.back.domain.auth.dto.response.LoginResponse;
import com.project.back.domain.auth.dto.response.TokenRefreshResponse;
import com.project.back.domain.auth.entity.RefreshToken;
import com.project.back.domain.auth.repository.RefreshTokenRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // 1. 이메일로 유저 찾기
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: loginId={}, reason=USER_NOT_FOUND, ip={}", request.getEmail(), ipAddress);
                    return new CustomException(ErrorCode.USER_NOT_FOUND);
                });

        // 2. 비밀번호 미설정 사용자 차단
        if (!user.isPasswordInitialized()) {
            log.warn("Login failed: loginId={}, reason=PASSWORD_NOT_INITIALIZED, ip={}", request.getEmail(), ipAddress);
            throw new CustomException(ErrorCode.PASSWORD_NOT_INITIALIZED);
        }

        // 3. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: loginId={}, reason=INVALID_PASSWORD, ip={}", request.getEmail(), ipAddress);
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        // 4. 유저 상태 검사
        validateUserStatus(user, request.getEmail(), ipAddress);

        // 5. 마지막 로그인 일시 기록
        user.updateLastLoginAt();

        // 6. JWT 액세스 토큰 발행
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        // 7. 리프레시 토큰 발행 (기존 토큰 교체)
        String rawRefreshToken = issueRefreshToken(user.getId());

        log.info("Login success: loginId={}, userId={}, ip={}", request.getEmail(), user.getId(), ipAddress);
        return LoginResponse.of(accessToken, rawRefreshToken, user.isMustChangePassword());
    }

    @Transactional
    public TokenRefreshResponse refresh(RefreshTokenRequest request) {
        String hashedToken = hashToken(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        validateUserStatus(user.getStatus());

        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        return TokenRefreshResponse.of(newAccessToken);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private String issueRefreshToken(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);
        long validityMs = jwtTokenProvider.getRefreshTokenValidityMs();
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(validityMs / 1000);

        refreshTokenRepository.save(RefreshToken.of(userId, hashedToken, expiryDate));
        return rawToken;
    }

    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void validateUserStatus(User user, String loginId, String ipAddress) {
        switch (user.getStatus()) {
            case SUSPENDED -> {
                log.warn("Login failed: loginId={}, reason=SUSPENDED_USER, ip={}", loginId, ipAddress);
                throw new CustomException(ErrorCode.USER_SUSPENDED);
            }
            case DELETED -> {
                log.warn("Login failed: loginId={}, reason=DELETED_USER, ip={}", loginId, ipAddress);
                throw new CustomException(ErrorCode.USER_DELETED);
            }
            case ACTIVE -> { /* 정상 유저 */ }
        }
    }
}
