package com.project.back.domain.auth.service;

import com.project.back.domain.auth.dto.request.LoginRequest;
import com.project.back.domain.auth.dto.response.LoginResponse;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("로그인 (이메일 + 비밀번호)")
    class Login {

        @Test
        @DisplayName("ACTIVE 사용자 - 로그인 성공, mustChangePassword=false 반환")
        void login_active_success() {
            LoginRequest request = mockLoginRequest("2026001@quoteguard.com", "Pass@1234");
            given(userRepository.findByEmail("2026001@quoteguard.com"))
                    .willReturn(Optional.of(buildUser(UserStatus.ACTIVE, false)));
            given(passwordEncoder.matches("Pass@1234", "encodedPassword")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(any(Long.class), anyString(), anyString()))
                    .willReturn("mock.jwt.token");

            LoginResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.isMustChangePassword()).isFalse();
        }

        @Test
        @DisplayName("최초 로그인(임시 비밀번호) - mustChangePassword=true 반환")
        void login_mustChangePassword_returnedInResponse() {
            LoginRequest request = mockLoginRequest("2026001@quoteguard.com", "QG-ABCD1234");
            given(userRepository.findByEmail("2026001@quoteguard.com"))
                    .willReturn(Optional.of(buildUser(UserStatus.ACTIVE, true)));
            given(passwordEncoder.matches("QG-ABCD1234", "encodedPassword")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(any(Long.class), anyString(), anyString()))
                    .willReturn("mock.jwt.token");

            LoginResponse response = authService.login(request);

            assertThat(response.isMustChangePassword()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 이메일 - USER_NOT_FOUND 예외")
        void login_emailNotFound() {
            LoginRequest request = mockLoginRequest("none@quoteguard.com", "Pass@1234");
            given(userRepository.findByEmail("none@quoteguard.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("비밀번호 불일치 - INVALID_PASSWORD 예외")
        void login_invalidPassword() {
            LoginRequest request = mockLoginRequest("2026001@quoteguard.com", "wrongPass");
            given(userRepository.findByEmail("2026001@quoteguard.com"))
                    .willReturn(Optional.of(buildUser(UserStatus.ACTIVE, false)));
            given(passwordEncoder.matches("wrongPass", "encodedPassword")).willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PASSWORD));
        }

        @Test
        @DisplayName("SUSPENDED 사용자 - USER_SUSPENDED 예외")
        void login_suspendedUser() {
            assertLoginStatusException(UserStatus.SUSPENDED, ErrorCode.USER_SUSPENDED);
        }

        @Test
        @DisplayName("DELETED 사용자 - USER_DELETED 예외")
        void login_deletedUser() {
            assertLoginStatusException(UserStatus.DELETED, ErrorCode.USER_DELETED);
        }

        private void assertLoginStatusException(UserStatus status, ErrorCode expectedCode) {
            LoginRequest request = mockLoginRequest("2026001@quoteguard.com", "Pass@1234");
            given(userRepository.findByEmail("2026001@quoteguard.com"))
                    .willReturn(Optional.of(buildUser(status, false)));
            given(passwordEncoder.matches("Pass@1234", "encodedPassword")).willReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(expectedCode));
        }
    }

    private LoginRequest mockLoginRequest(String email, String password) {
        try {
            LoginRequest req = new LoginRequest();
            setField(req, "email", email);
            setField(req, "password", password);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User buildUser(UserStatus status, boolean mustChangePassword) {
        return User.builder()
                .id(1L)
                .memberNumber("2026001")
                .email("2026001@quoteguard.com")
                .password("encodedPassword")
                .name("테스터")
                .role(UserRole.SALES_STAFF)
                .status(status)
                .mustChangePassword(mustChangePassword)
                .build();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
