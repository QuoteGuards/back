package com.project.back.domain.auth.service;

import com.project.back.domain.auth.entity.PasswordResetToken;
import com.project.back.domain.auth.entity.TokenPurpose;
import com.project.back.domain.auth.event.InitialPasswordSetupEmailEvent;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("초기 비밀번호 설정 서비스 테스트")
class InitialPasswordSetupServiceTest {

    @InjectMocks
    private InitialPasswordSetupService service;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "tokenExpiryMinutes", 1440);
    }

    // ── 공통 헬퍼 ──────────────────────────────────────────────────────────

    private User buildUser(boolean passwordInitialized) {
        User user = User.builder()
                .memberNumber("2612345")
                .email("2612345@quoteguard.com")
                .password("encoded_placeholder")
                .name("홍길동")
                .role(UserRole.SALES_STAFF)
                .status(UserStatus.ACTIVE)
                .passwordInitialized(passwordInitialized)
                .mustChangePassword(false)
                .build();
        setField(user, "id", 1L);
        setField(user, "createdAt", LocalDateTime.now());
        setField(user, "updatedAt", LocalDateTime.now());
        return user;
    }

    private PasswordResetToken buildToken(TokenPurpose purpose, boolean used, boolean expired) {
        LocalDateTime expiresAt = expired
                ? LocalDateTime.now().minusMinutes(1)
                : LocalDateTime.now().plusMinutes(1440);
        LocalDateTime usedAt = used ? LocalDateTime.now().minusMinutes(5) : null;

        PasswordResetToken token = PasswordResetToken.builder()
                .userId(1L)
                .tokenHash("hashed_token")
                .purpose(purpose)
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .build();
        setField(token, "id", 1L);
        setField(token, "createdAt", LocalDateTime.now().minusMinutes(10));
        return token;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 초기 비밀번호 설정 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("초기 비밀번호 설정")
    class SetInitialPassword {

        @Test
        @DisplayName("유효한 토큰으로 비밀번호 설정 성공")
        void setInitialPassword_success() {
            PasswordResetToken token = buildToken(TokenPurpose.INITIAL_PASSWORD_SETUP, false, false);
            User user = buildUser(false);

            given(tokenRepository.markUsedIfValid(anyString(), any(TokenPurpose.class), any())).willReturn(1);
            given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.encode("ValidPass1!")).willReturn("encoded_new");

            service.setInitialPassword("raw_token", "ValidPass1!", "ValidPass1!");

            assertThat(user.isPasswordInitialized()).isTrue();
            assertThat(user.isMustChangePassword()).isFalse();
            verify(passwordEncoder).encode("ValidPass1!");
        }

        @Test
        @DisplayName("비밀번호 확인 불일치 시 PASSWORD_CONFIRM_MISMATCH 예외")
        void setInitialPassword_passwordMismatch() {
            assertThatThrownBy(() ->
                    service.setInitialPassword("token", "ValidPass1!", "Different1!")
            )
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PASSWORD_CONFIRM_MISMATCH));

            verify(tokenRepository, never()).markUsedIfValid(anyString(), any(TokenPurpose.class), any());
        }

        @Test
        @DisplayName("이미 사용된 토큰 → INIT_PASSWORD_TOKEN_ALREADY_USED 예외")
        void setInitialPassword_alreadyUsedToken() {
            PasswordResetToken usedToken = buildToken(TokenPurpose.INITIAL_PASSWORD_SETUP, true, false);

            given(tokenRepository.markUsedIfValid(anyString(), any(TokenPurpose.class), any())).willReturn(0);
            given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(usedToken));

            assertThatThrownBy(() ->
                    service.setInitialPassword("raw_token", "ValidPass1!", "ValidPass1!")
            )
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INIT_PASSWORD_TOKEN_ALREADY_USED));
        }

        @Test
        @DisplayName("만료된 토큰 → INIT_PASSWORD_TOKEN_EXPIRED 예외")
        void setInitialPassword_expiredToken() {
            PasswordResetToken expiredToken = buildToken(TokenPurpose.INITIAL_PASSWORD_SETUP, false, true);

            given(tokenRepository.markUsedIfValid(anyString(), any(TokenPurpose.class), any())).willReturn(0);
            given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(expiredToken));

            assertThatThrownBy(() ->
                    service.setInitialPassword("raw_token", "ValidPass1!", "ValidPass1!")
            )
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INIT_PASSWORD_TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("토큰이 없으면 INIT_PASSWORD_TOKEN_INVALID 예외")
        void setInitialPassword_invalidToken() {
            given(tokenRepository.markUsedIfValid(anyString(), any(TokenPurpose.class), any())).willReturn(0);
            given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.setInitialPassword("raw_token", "ValidPass1!", "ValidPass1!")
            )
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INIT_PASSWORD_TOKEN_INVALID));
        }

        @Test
        @DisplayName("PURPOSE_MISMATCH - PASSWORD_RESET 토큰을 초기 설정에 사용 불가")
        void setInitialPassword_wrongPurpose() {
            PasswordResetToken wrongToken = buildToken(TokenPurpose.PASSWORD_RESET, false, false);

            // purpose=INITIAL_PASSWORD_SETUP을 WHERE 조건에 포함했으므로 PASSWORD_RESET 토큰은 UPDATE 실패 → 0
            given(tokenRepository.markUsedIfValid(anyString(), any(TokenPurpose.class), any())).willReturn(0);
            given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(wrongToken));
            // purpose 검증 실패로 userRepository.findById 는 호출되지 않으므로 스텁 불필요

            assertThatThrownBy(() ->
                    service.setInitialPassword("raw_token", "ValidPass1!", "ValidPass1!")
            )
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INIT_PASSWORD_TOKEN_PURPOSE_MISMATCH));
        }

        @Test
        @DisplayName("이미 비밀번호를 설정한 사용자 → INIT_PASSWORD_ALREADY_SET 예외")
        void setInitialPassword_alreadyInitialized() {
            PasswordResetToken token = buildToken(TokenPurpose.INITIAL_PASSWORD_SETUP, false, false);
            User user = buildUser(true); // 이미 설정됨

            given(tokenRepository.markUsedIfValid(anyString(), any(TokenPurpose.class), any())).willReturn(1);
            given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThatThrownBy(() ->
                    service.setInitialPassword("raw_token", "ValidPass1!", "ValidPass1!")
            )
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INIT_PASSWORD_ALREADY_SET));
        }
    }

    // ── 링크 재발송 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("초기 비밀번호 설정 링크 재발송")
    class ResendSetupLink {

        @Test
        @DisplayName("이미 비밀번호를 설정한 사용자는 재발송 불가 → INIT_PASSWORD_ALREADY_SET")
        void resendSetupLink_alreadyInitialized() {
            User user = buildUser(true);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> service.resendSetupLink(1L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INIT_PASSWORD_ALREADY_SET));
        }

        @Test
        @DisplayName("쿨다운(60초) 이내 재발송 시 INIT_PASSWORD_RESEND_TOO_SOON 예외")
        void resendSetupLink_cooldownViolation() {
            User user = buildUser(false);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // 30초 전에 생성된 토큰 → 아직 쿨다운 중
            PasswordResetToken recentToken = buildToken(TokenPurpose.INITIAL_PASSWORD_SETUP, false, false);
            setField(recentToken, "createdAt", LocalDateTime.now().minusSeconds(30));
            given(tokenRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(1L, TokenPurpose.INITIAL_PASSWORD_SETUP))
                    .willReturn(Optional.of(recentToken));

            assertThatThrownBy(() -> service.resendSetupLink(1L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INIT_PASSWORD_RESEND_TOO_SOON));
        }

        @Test
        @DisplayName("쿨다운 경과 후 재발송 성공 — 이전 토큰 무효화 및 이벤트 발행")
        void resendSetupLink_afterCooldown_success() {
            User user = buildUser(false);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // 2분 전에 생성된 토큰 → 쿨다운 경과
            PasswordResetToken oldToken = buildToken(TokenPurpose.INITIAL_PASSWORD_SETUP, false, false);
            setField(oldToken, "createdAt", LocalDateTime.now().minusMinutes(2));
            given(tokenRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(1L, TokenPurpose.INITIAL_PASSWORD_SETUP))
                    .willReturn(Optional.of(oldToken));
            given(tokenRepository.save(any(PasswordResetToken.class))).willReturn(oldToken);

            service.resendSetupLink(1L);

            verify(tokenRepository).expireAllActiveByUserIdAndPurpose(
                    eq(1L), eq(TokenPurpose.INITIAL_PASSWORD_SETUP), any());
            verify(tokenRepository).save(any(PasswordResetToken.class));
            // InitialPasswordSetupEmailEvent는 ApplicationEvent 미상속 record이므로
            // publishEvent(Object) 오버로드로 호출됨 → any(Class)로 오버로드 명시
            verify(eventPublisher).publishEvent(any(InitialPasswordSetupEmailEvent.class));
        }
    }
}
