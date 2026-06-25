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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    // 로그인 (이메일 + 비밀번호)
    // 이메일은 관리자가 계정 생성 시 {memberNumber}@domain 형식으로 자동 생성된다.
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. 이메일로 유저 찾기
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        // 3. 유저 상태 검사
        validateUserStatus(user.getStatus());

        // 4. 마지막 로그인 일시 기록
        user.updateLastLoginAt();

        // 5. JWT 액세스 토큰 발행
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        // 6. 리프레시 토큰 발행 (기존 토큰 교체)
        String rawRefreshToken = issueRefreshToken(user.getId());

        return LoginResponse.of(accessToken, rawRefreshToken, user.isMustChangePassword());
    }

    // 액세스 토큰 재발급
    @Transactional
    public TokenRefreshResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.getRefreshToken())
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

    // 로그아웃: 리프레시 토큰 삭제
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    // 리프레시 토큰 생성 및 DB 저장 (기존 토큰 교체)
    private String issueRefreshToken(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);

        String rawToken = UUID.randomUUID().toString();
        long validityMs = jwtTokenProvider.getRefreshTokenValidityMs();
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(validityMs / 1000);

        refreshTokenRepository.save(RefreshToken.of(userId, rawToken, expiryDate));
        return rawToken;
    }

    private void validateUserStatus(UserStatus status) {
        switch (status) {
            case SUSPENDED -> throw new CustomException(ErrorCode.USER_SUSPENDED);
            case DELETED -> throw new CustomException(ErrorCode.USER_DELETED);
            case ACTIVE -> { /* 정상 유저 */ }
        }
    }
}
