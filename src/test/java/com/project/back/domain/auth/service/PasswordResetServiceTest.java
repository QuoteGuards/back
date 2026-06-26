package com.project.back.domain.auth.service;

import com.project.back.domain.auth.entity.PasswordResetToken;
import com.project.back.domain.auth.event.PasswordResetEmailEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "tokenExpiryMinutes", 30);
    }

    // --- requestReset ---

    @Nested
    @DisplayName("requestReset")
    class RequestReset {

        @Test
        @DisplayName("등록된 이메일 - 기존 토큰 무효화 후 새 토큰 저장 및 이메일 발송 이벤트 발행")
        void existingEmail_invalidatesOldTokens_savesNew_publishesEvent() {
            User user = buildUser();
            given(userRepository.findByEmail("test@quoteguard.com")).willReturn(Optional.of(user));

            passwordResetService.requestReset("test@quoteguard.com");

            verify(passwordResetTokenRepository).expireAllActiveByUserId(eq(1L), any(LocalDateTime.class));
            verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
            // 이메일 발송이 트랜잭션 외부(AFTER_COMMIT)로 위임되었는지 이벤트 발행으로 검증
            verify(eventPublisher).publishEvent(any(PasswordResetEmailEvent.class));
        }

        @Test
        @DisplayName("미등록 이메일 - 예외 없이 정상 종료 (계정 존재 여부 미노출)")
        void unknownEmail_noExceptionThrown() {
            given(userRepository.findByEmail("unknown@email.com")).willReturn(Optional.empty());

            passwordResetService.requestReset("unknown@email.com");

            verifyNoInteractions(passwordResetTokenRepository, eventPublisher);
        }

        @Test
        @DisplayName("발행된 이벤트에 userId, userEmail, rawToken이 포함된다")
        void publishedEvent_containsExpectedFields() {
            User user = buildUser();
            given(userRepository.findByEmail("test@quoteguard.com")).willReturn(Optional.of(user));

            passwordResetService.requestReset("test@quoteguard.com");

            ArgumentCaptor<PasswordResetEmailEvent> eventCaptor =
                    ArgumentCaptor.forClass(PasswordResetEmailEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            PasswordResetEmailEvent event = eventCaptor.getValue();
            assertThat(event.userId()).isEqualTo(1L);
            assertThat(event.userEmail()).isEqualTo("test@quoteguard.com");
            assertThat(event.rawToken()).isNotBlank();
        }

        @Test
        @DisplayName("저장된 토큰은 원문이 아닌 SHA-256 해시")
        void savedToken_isHashed_notPlaintext() {
            User user = buildUser();
            given(userRepository.findByEmail("test@quoteguard.com")).willReturn(Optional.of(user));

            passwordResetService.requestReset("test@quoteguard.com");

            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordResetTokenRepository).save(captor.capture());

            String savedHash = captor.getValue().getTokenHash();
            // SHA-256: 32 bytes = 64 hex chars
            assertThat(savedHash).hasSize(64).matches("[0-9a-f]{64}");
        }
    }

    // --- confirmReset ---

    @Nested
    @DisplayName("confirmReset")
    class ConfirmReset {

        @Test
        @DisplayName("유효한 토큰 - 원자적 UPDATE 성공 후 비밀번호 변경")
        void validToken_atomicUpdateSucceeds_changesPassword() {
            String rawToken = "a".repeat(64);
            String tokenHash = sha256(rawToken);
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

            PasswordResetToken token = PasswordResetToken.of(1L, tokenHash, expiresAt);
            User user = buildUser();

            // markUsedIfValid: 1 반환 → 원자적 UPDATE 성공
            given(passwordResetTokenRepository.markUsedIfValid(eq(tokenHash), any(LocalDateTime.class)))
                    .willReturn(1);
            // UPDATE 성공 후 userId 조회용 findByTokenHash
            given(passwordResetTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(token));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.encode("NewPass1!")).willReturn("encodedNewPass");

            passwordResetService.confirmReset(rawToken, "NewPass1!");

            verify(passwordEncoder).encode("NewPass1!");
        }

        @Test
        @DisplayName("존재하지 않는 토큰 - PASSWORD_RESET_TOKEN_INVALID")
        void invalidToken_throwsException() {
            String rawToken = "b".repeat(64);
            String tokenHash = sha256(rawToken);

            // markUsedIfValid: 0 반환 → 조건 불충족
            given(passwordResetTokenRepository.markUsedIfValid(eq(tokenHash), any(LocalDateTime.class)))
                    .willReturn(0);
            // 세부 원인 파악을 위해 findByTokenHash 호출 → 토큰 없음
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

            PasswordResetToken usedToken = PasswordResetToken.of(1L, tokenHash,
                    LocalDateTime.now().plusMinutes(30));
            usedToken.markUsed();

            given(passwordResetTokenRepository.markUsedIfValid(eq(tokenHash), any(LocalDateTime.class)))
                    .willReturn(0);
            given(passwordResetTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(usedToken));

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

            PasswordResetToken expiredToken = PasswordResetToken.of(1L, tokenHash,
                    LocalDateTime.now().minusMinutes(1));

            given(passwordResetTokenRepository.markUsedIfValid(eq(tokenHash), any(LocalDateTime.class)))
                    .willReturn(0);
            given(passwordResetTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(expiredToken));

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
