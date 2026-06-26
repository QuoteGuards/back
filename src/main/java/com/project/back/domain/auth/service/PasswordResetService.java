package com.project.back.domain.auth.service;

import com.project.back.domain.auth.entity.PasswordResetToken;
import com.project.back.domain.auth.event.PasswordResetEmailEvent;
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
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.password-reset-token-expiry-minutes:30}")
    private int tokenExpiryMinutes;

    private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit

    /**
     * 비밀번호 재설정 이메일 요청
     *
     * <p>이메일이 존재하지 않아도 동일한 응답을 반환한다. (계정 존재 여부 노출 방지)</p>
     * <p>이메일 발송은 트랜잭션 커밋 이후 {@code @TransactionalEventListener}로 처리된다.
     * 발송 실패 시에도 호출자에게 동일 응답(200)을 반환하므로 응답 차이로 계정 존재 여부가
     * 노출되지 않는다.</p>
     */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            // 기존 활성 토큰 무효화
            passwordResetTokenRepository.expireAllActiveByUserId(user.getId(), LocalDateTime.now());

            // 새 토큰 생성 (원문은 이메일 링크에만 포함, DB에는 해시만 저장)
            String rawToken = generateRawToken();
            String tokenHash = hashToken(rawToken);
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenExpiryMinutes);

            passwordResetTokenRepository.save(
                    PasswordResetToken.of(user.getId(), tokenHash, expiresAt)
            );

            // 이메일 발송을 AFTER_COMMIT 이벤트로 위임:
            // - 커밋 전 발송 후 롤백 시 "링크는 있으나 토큰 없음" 불일치 방지
            // - 발송 실패가 트랜잭션 롤백으로 이어지지 않으므로 계정 열거 방어 유지
            eventPublisher.publishEvent(
                    PasswordResetEmailEvent.of(user.getId(), user.getName(), user.getEmail(), rawToken)
            );
        });
    }

    /**
     * 비밀번호 재설정 확인
     *
     * <p>토큰 검증과 사용 처리를 단일 UPDATE로 원자적으로 수행한다.
     * 동시 요청에서 한 건만 성공하고 나머지는 오류로 처리된다.</p>
     */
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now();

        // 원자적 UPDATE: usedAt IS NULL AND expiresAt > now 조건 만족 시에만 성공
        int updated = passwordResetTokenRepository.markUsedIfValid(tokenHash, now);

        if (updated == 0) {
            // 0건이면 토큰이 없거나, 이미 사용됐거나, 만료된 것
            // 세부 원인을 구분해 적절한 오류를 반환한다
            PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                    .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

            if (token.isUsed()) {
                throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED);
            }
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
        }

        // UPDATE 성공 후 userId 조회를 위해 토큰을 가져온다
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.changePassword(passwordEncoder.encode(newPassword));
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
