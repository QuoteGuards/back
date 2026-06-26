package com.project.back.domain.auth.service;

import com.project.back.domain.auth.entity.PasswordResetToken;
import com.project.back.domain.auth.repository.PasswordResetTokenRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
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
    private final JavaMailSender mailSender;

    @Value("${mail.from-address}")
    private String fromAddress;

    @Value("${mail.from-name:QuoteGuard}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.password-reset-token-expiry-minutes:30}")
    private int tokenExpiryMinutes;

    private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit

    /**
     * 비밀번호 재설정 이메일 요청
     * 이메일이 존재하지 않아도 동일한 응답을 반환한다. (계정 존재 여부 노출 방지)
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

            sendResetEmail(user, rawToken);
        });
    }

    /**
     * 비밀번호 재설정 확인
     * 토큰 검증 후 새 비밀번호로 변경하고 토큰을 사용 처리한다.
     */
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        if (token.isUsed()) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED);
        }

        if (token.isExpired()) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed();
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

    private void sendResetEmail(User user, String rawToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());

            helper.setFrom (fromAddress, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject("[QuoteGuard] 비밀번호 재설정 안내");
            helper.setText(buildEmailBody(user.getName(), resetLink), true);

            mailSender.send(message);

        } catch (MessagingException | RuntimeException e) {
            log.error("비밀번호 재설정 이메일 발송 실패 - userId={}", user.getId(), e);
            throw new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildEmailBody(String userName, String resetLink) {
        return String.format("""
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#1f2937">
                  <h2 style="font-size:18px;font-weight:600;margin-bottom:8px">비밀번호 재설정 안내</h2>
                  <p style="font-size:14px;color:#374151;margin-bottom:24px">
                    안녕하세요, <strong>%s</strong> 님.<br>
                    아래 버튼을 클릭하여 비밀번호를 재설정하세요.<br>
                    링크는 <strong>%d분</strong> 후 만료됩니다.
                  </p>
                  <a href="%s"
                     style="display:inline-block;padding:12px 24px;background-color:#2563eb;color:#ffffff;font-size:14px;font-weight:600;text-decoration:none;border-radius:6px">
                    비밀번호 재설정
                  </a>
                  <p style="font-size:12px;color:#9ca3af;margin-top:24px">
                    버튼이 동작하지 않으면 아래 링크를 복사하여 브라우저에 붙여넣으세요.<br>
                    <a href="%s" style="color:#6b7280;word-break:break-all">%s</a>
                  </p>
                  <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0">
                  <p style="font-size:12px;color:#9ca3af">
                    본인이 요청하지 않은 경우 이 메일을 무시하셔도 됩니다.
                  </p>
                </div>
                """, userName, tokenExpiryMinutes, resetLink, resetLink, resetLink);
    }
}
