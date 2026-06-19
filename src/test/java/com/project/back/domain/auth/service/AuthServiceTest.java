package com.project.back.domain.auth.service;

import com.project.back.domain.auth.dto.request.LoginRequest;
import com.project.back.domain.auth.dto.request.SignUpRequest;
import com.project.back.domain.auth.dto.response.LoginResponse;
import com.project.back.domain.auth.dto.response.SignUpResponse;
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
import static org.mockito.Mockito.verify;

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
    @DisplayName("회원가입")
    class SignUp {

        @Test
        @DisplayName("정상 회원가입 - role은 SALES_STAFF, status는 PENDING")
        void signUp_success() {
            SignUpRequest request = mockSignUpRequest("test@example.com", "Pass@1234", "홍길동", null);
            given(userRepository.existsByEmail("test@example.com")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
            given(userRepository.save(any(User.class))).willReturn(
                    User.builder().email("test@example.com").password("encodedPassword").name("홍길동").build()
            );

            SignUpResponse response = authService.signUp(request);

            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.getRole()).isEqualTo(UserRole.SALES_STAFF.name());
            assertThat(response.getStatus()).isEqualTo(UserStatus.PENDING.name());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("중복 이메일 - DUPLICATE_EMAIL 예외")
        void signUp_duplicateEmail() {
            SignUpRequest request = mockSignUpRequest("exist@example.com", "Pass@1234", "홍길동", null);
            given(userRepository.existsByEmail("exist@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.signUp(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_EMAIL));
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("APPROVED 사용자 - 로그인 성공, Access Token 발급")
        void login_approved_success() {
            LoginRequest request = mockLoginRequest("approved@example.com", "Pass@1234");
            given(userRepository.findByEmail("approved@example.com"))
                    .willReturn(Optional.of(buildUser(UserStatus.APPROVED)));
            given(passwordEncoder.matches("Pass@1234", "encodedPassword")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(any(Long.class), anyString(), anyString()))
                    .willReturn("mock.jwt.token");

            LoginResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("존재하지 않는 이메일 - USER_NOT_FOUND 예외")
        void login_userNotFound() {
            LoginRequest request = mockLoginRequest("none@example.com", "Pass@1234");
            given(userRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("비밀번호 불일치 - INVALID_PASSWORD 예외")
        void login_invalidPassword() {
            LoginRequest request = mockLoginRequest("approved@example.com", "wrongPass@1");
            given(userRepository.findByEmail("approved@example.com"))
                    .willReturn(Optional.of(buildUser(UserStatus.APPROVED)));
            given(passwordEncoder.matches("wrongPass@1", "encodedPassword")).willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PASSWORD));
        }

        @Test
        @DisplayName("PENDING 사용자 - USER_PENDING 예외")
        void login_pendingUser() {
            assertLoginStatusException(UserStatus.PENDING, ErrorCode.USER_PENDING);
        }

        @Test
        @DisplayName("REJECTED 사용자 - USER_REJECTED 예외")
        void login_rejectedUser() {
            assertLoginStatusException(UserStatus.REJECTED, ErrorCode.USER_REJECTED);
        }

        @Test
        @DisplayName("SUSPENDED 사용자 - USER_SUSPENDED 예외")
        void login_suspendedUser() {
            assertLoginStatusException(UserStatus.SUSPENDED, ErrorCode.USER_SUSPENDED);
        }

        private void assertLoginStatusException(UserStatus status, ErrorCode expectedCode) {
            LoginRequest request = mockLoginRequest("user@example.com", "Pass@1234");
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(buildUser(status)));
            given(passwordEncoder.matches("Pass@1234", "encodedPassword")).willReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(expectedCode));
        }
    }

    // ---- helpers ----

    private SignUpRequest mockSignUpRequest(String email, String password, String name, String phone) {
        try {
            SignUpRequest req = new SignUpRequest();
            setField(req, "email", email);
            setField(req, "password", password);
            setField(req, "name", name);
            setField(req, "phone", phone);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    private User buildUser(UserStatus status) {
        return User.builder()
                .email("user@example.com")
                .password("encodedPassword")
                .name("테스터")
                .status(status)
                .build();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
