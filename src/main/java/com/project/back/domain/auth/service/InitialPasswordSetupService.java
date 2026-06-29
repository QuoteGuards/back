package com.project.back.domain.auth.service;

import com.project.back.domain.auth.entity.PasswordResetToken;
import com.project.back.domain.auth.entity.TokenPurpose;
import com.project.back.domain.auth.event.InitialPasswordSetupEmailEvent;
import com.project.back.domain.auth.repository.PasswordResetTokenRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitialPasswordSetupService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.initial-password-token-expiry-minutes:1440}")
    private int tokenExpiryMinutes;

    /** 재발송 최소 대기 시간 (초) */
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private static final int TOKEN_BYTE_LENGTH = 32;

    /**
     * 사용자 생성 직후 호출: 초기 비밀번호 설정 토큰 생성 및 이메일 발송 이벤트 발행
     * 트랜잭션 내에서 호출되며, 이메일은 AFTER_COMMIT 이후 발송된다.
     */
    @Transactional
    public void sendSetupLink(User user) {
        // 기존 미사용 초기 설정 토큰 모두 무효화
        tokenRepository.expireAllActiveByUserIdAndPurpose(
                user.getId(), TokenPurpose.INITIAL_PASSWORD_SETUP, LocalDateTime.now());

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenExpiryMinutes);

        tokenRepository.save(
                PasswordResetToken.ofInitialSetup(user.getId(), tokenHash, expiresAt)
        );

        eventPublisher.publishEvent(
                InitialPasswordSetupEmailEvent.of(user.getId(), user.getName(), user.getEmail(), rawToken)
        );
    }

    /**
     * 초기 비밀번호 설정 완료
     */
    @Transactional
    public void setInitialPassword(String rawToken, String newPassword, String newPasswordConfirm) {
        // 비밀번호 확인 일치 검증
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new CustomException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        String tokenHash = hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now();

        // 원자적 UPDATE: 미사용·미만료 토큰만 사용 처리
        int updated = tokenRepository.markUsedIfValid(tokenHash, now);

        if (updated == 0) {
            PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                    .orElseThrow(() -> new CustomException(ErrorCode.INIT_PASSWORD_TOKEN_INVALID));

            if (token.isUsed()) {
                throw new CustomException(ErrorCode.INIT_PASSWORD_TOKEN_ALREADY_USED);
            }
            throw new CustomException(ErrorCode.INIT_PASSWORD_TOKEN_EXPIRED);
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.INIT_PASSWORD_TOKEN_INVALID));

        // purpose 검증: INITIAL_PASSWORD_SETUP 토큰만 허용
        if (token.getPurpose() != TokenPurpose.INITIAL_PASSWORD_SETUP) {
            throw new CustomException(ErrorCode.INIT_PASSWORD_TOKEN_PURPOSE_MISMATCH);
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 설정 완료된 경우 차단
        if (user.isPasswordInitialized()) {
            throw new CustomException(ErrorCode.INIT_PASSWORD_ALREADY_SET);
        }

        user.setInitialPassword(passwordEncoder.encode(newPassword));
        log.info("초기 비밀번호 설정 완료 - userId={}", user.getId());
    }

    /**
     * 관리자가 초기 비밀번호 설정 링크 재발송
     */
    @Transactional
    public void resendSetupLink(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 비밀번호를 설정한 사용자는 재발송 불가
        if (user.isPasswordInitialized()) {
            throw new CustomException(ErrorCode.INIT_PASSWORD_ALREADY_SET);
        }

        // 쿨다운 확인: 최근 토큰 생성 시각이 60초 이내면 거부
        tokenRepository.findLatestByUserIdAndPurpose(userId, TokenPurpose.INITIAL_PASSWORD_SETUP)
                .ifPresent(latest -> {
                    LocalDateTime cooldownEnd = latest.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
                    if (LocalDateTime.now().isBefore(cooldownEnd)) {
                        throw new CustomException(ErrorCode.INIT_PASSWORD_RESEND_TOO_SOON);
                    }
                });

        sendSetupLink(user);
        log.info("초기 비밀번호 설정 링크 재발송 - userId={}", userId);
    }

    // --- private helpers ---

    private String generateRawToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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
}
