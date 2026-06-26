package com.project.back.domain.auth.service;

import com.project.back.domain.auth.entity.PasswordResetToken;
import com.project.back.domain.auth.repository.PasswordResetTokenRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "fromAddress", "no-reply@quoteguard.com");
        ReflectionTestUtils.setField(passwordResetService, "fromName", "QuoteGuard");
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(passwordResetService, "tokenExpiryMinutes", 30);
    }

    // --- requestReset ---

    @Nested
    @DisplayName("requestReset")
    class RequestReset {

        @Test
        @DisplayName("등록된 이메일 - 기존 토큰 무효화 후 새 토큰 저장 및 이메일 발송")
        void existingEmail_invalidatesOldTokens_savesNew_sendsEmail() throws Exception {
            User user = buildUser();
            MimeMessage mimeMessage = mock(MimeMessage.class);
            given(userRepository.findByEmail("test@quoteguard.com")).willReturn(Optional.of(user));
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            passwordResetService.requestReset("test@quoteguard.com");

            verify(passwordResetTokenRepository).expireAllActiveByUserId(eq(1L), any(LocalDateTime.class));
            verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("미등록 이메일 - 예외 없이 정상 종료 (계정 존재 여부 미노출)")
        void unknownEmail_noExceptionThrown() {
            given(userRepository.findByEmail("unknown@email.com")).willReturn(Optional.empty());

            // 예외 없이 종료되어야 한다
            passwordResetService.requestReset("unknown@email.com");

            verifyNoInteractions(passwordResetTokenRepository, mailSender);
        }

        @Test
        @DisplayName("저장된 토큰은 원문이 아닌 SHA-256 해시")
        void savedToken_isHashed_notPlaintext() {
            User user = buildUser();
            MimeMessage mimeMessage = mock(MimeMessage.class);
            given(userRepository.findByEmail("test@quoteguard.com")).willReturn(Optional.of(user));
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            passwordResetService.requestReset("test@quoteguard.com");

            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordResetTokenRepository).save(captor.capture());

            String savedHash = captor.getValue().getTokenHash();
            // 해시는 64자 hex 문자열이어야 한다 (SHA-256: 32 bytes = 64 hex chars)
            assertThat(savedHash).hasSize(64);
            // 해시가 0-9, a-f 문자로만 구성되어야 한다
            assertThat(savedHash).matches("[0-9a-f]{64}");
        }
    }

    // --- confirmReset ---

    @Nested
    @DisplayName("confirmReset")
    class ConfirmReset {

        @Test
        @DisplayName("유효한 토큰 - 비밀번호 변경 및 토큰 사용 처리")
        void validToken_changesPasswordAndMarksUsed() {
            String rawToken = "a".repeat(64); // 임의의 64자 토큰
            String tokenHash = sha256(rawToken);

            PasswordResetToken token = PasswordResetToken.of(1L, tokenHash,
                    LocalDateTime.now().plusMinutes(30));
            User user = buildUser();

            given(passwordResetTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(token));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.encode("NewPass1!")).willReturn("encodedNewPass");

            passwordResetService.confirmReset(rawToken, "NewPass1!");

            assertThat(token.isUsed()).isTrue();
            verify(passwordEncoder).encode("NewPass1!");
        }

        @Test
        @DisplayName("존재하지 않는 토큰 - PASSWORD_RESET_TOKEN_INVALID")
        void invalidToken_throwsException() {
            String rawToken = "b".repeat(64);
            String tokenHash = sha256(rawToken);

            given(passwordResetTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.empty());

            assertThatThrownBy(() -> passwordResetService.confirmReset(rawToken, "NewPass1!"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        @Test
        @DisplayName("이미 사용된 토큰 - PASSWORD_RESET_TOKEN_ALREADY_USED")
        void alreadyUsedToken_throwsException() {
            String rawToken = "c".repeat(64);
            String tokenHash = sha256(rawToken);

            PasswordResetToken token = PasswordResetToken.of(1L, tokenHash,
                    LocalDateTime.now().plusMinutes(30));
            token.markUsed(); // 사용 처리

            given(passwordResetTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(token));

            assertThatThrownBy(() -> passwordResetService.confirmReset(rawToken, "NewPass1!"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED);
        }

        @Test
        @DisplayName("만료된 토큰 - PASSWORD_RESET_TOKEN_EXPIRED")
        void expiredToken_throwsException() {
            String rawToken = "d".repeat(64);
            String tokenHash = sha256(rawToken);

            PasswordResetToken token = PasswordResetToken.of(1L, tokenHash,
                    LocalDateTime.now().minusMinutes(1)); // 이미 만료

            given(passwordResetTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(token));

            assertThatThrownBy(() -> passwordResetService.confirmReset(rawToken, "NewPass1!"))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
        }
    }

    // --- helpers ---

    private User buildUser() {
        return User.builder()
                .id(1L)
                .memberNumber("2026001")
                .email("test@quoteguard.com")
                .password("encodedPassword")
                .name("테스터")
                .role(UserRole.SALES_STAFF)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build();
    }

    private String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
