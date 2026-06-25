package com.project.back.domain.auth.service;

import com.project.back.domain.auth.dto.request.LoginRequest;
import com.project.back.domain.auth.dto.request.RefreshTokenRequest;
import com.project.back.domain.auth.dto.response.LoginResponse;
import com.project.back.domain.auth.dto.response.TokenRefreshResponse;
import com.project.back.domain.auth.entity.RefreshToken;
import com.project.back.domain.auth.repository.RefreshTokenRepository;
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

import java.time.LocalDateTime;
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

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("ACTIVE - success, mustChangePassword=false")
        void login_active_success() {
            LoginRequest request = mockLoginRequest("2026001@quoteguard.com", "Pass@1234");
            given(userRepository.findByEmail("2026001@quoteguard.com"))
                    .willReturn(Optional.of(buildUser(UserStatus.ACTIVE, false)));
            given(passwordEncoder.matches("Pass@1234", "encodedPassword")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(any(Long.class), anyString(), anyString()))
                    .willReturn("mock.jwt.token");
            given(jwtTokenProvider.getRefreshTokenValidityMs()).willReturn(604800000L);
            given(refreshTokenRepository.save(any(RefreshToken.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            LoginResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
            assertThat(response.getRefreshToken()).isNotBlank();
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.isMustChangePassword()).isFalse();
        }

        @Test
        @DisplayName("mustChangePassword=true - returned in response")
        void login_mustChangePassword_returnedInResponse() {
            LoginRequest request = mockLoginRequest("2026001@quoteguard.com", "QG-ABCD1234");
            given(userRepository.findByEmail("2026001@quoteguard.com"))
                    .willReturn(Optional.of(buildUser(UserStatus.ACTIVE, true)));
            given(passwordEncoder.matches("QG-ABCD1234", "encodedPassword")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(any(Long.class), anyString(), anyString()))
                    .willReturn("mock.jwt.token");
            given(jwtTokenProvider.getRefreshTokenValidityMs()).willReturn(604800000L);
            given(refreshTokenRepository.save(any(RefreshToken.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            LoginResponse response = authService.login(request);

            assertThat(response.isMustChangePassword()).isTrue();
        }

        @Test
        @DisplayName("USER_NOT_FOUND exception")
        void login_emailNotFound() {
            LoginRequest request = mockLoginRequest("none@quoteguard.com", "Pass@1234");
            given(userRepository.findByEmail("none@quoteguard.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("INVALID_PASSWORD exception")
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
        @DisplayName("SUSPENDED - USER_SUSPENDED exception")
        void login_suspendedUser() {
            assertLoginStatusException(UserStatus.SUSPENDED, ErrorCode.USER_SUSPENDED);
        }

        @Test
        @DisplayName("DELETED - USER_DELETED exception")
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

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("valid refresh token - new access token issued")
        void refresh_success() throws Exception {
            RefreshToken stored = RefreshToken.of(1L, "valid-refresh-token",
                    LocalDateTime.now().plusDays(7));

            RefreshTokenRequest request = new RefreshTokenRequest();
            setField(request, "refreshToken", "valid-refresh-token");

            given(refreshTokenRepository.findByToken("valid-refresh-token"))
                    .willReturn(Optional.of(stored));
            given(userRepository.findById(1L))
                    .willReturn(Optional.of(buildUser(UserStatus.ACTIVE, false)));
            given(jwtTokenProvider.createAccessToken(any(Long.class), anyString(), anyString()))
                    .willReturn("new.access.token");

            TokenRefreshResponse response = authService.refresh(request);

            assertThat(response.getAccessToken()).isEqualTo("new.access.token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("REFRESH_TOKEN_NOT_FOUND exception")
        void refresh_tokenNotFound() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest();
            setField(request, "refreshToken", "unknown-token");
            given(refreshTokenRepository.findByToken("unknown-token")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
        }

        @Test
        @DisplayName("expired refresh token - REFRESH_TOKEN_EXPIRED exception")
        void refresh_tokenExpired() throws Exception {
            RefreshToken expired = RefreshToken.of(1L, "expired-token",
                    LocalDateTime.now().minusDays(1));

            RefreshTokenRequest request = new RefreshTokenRequest();
            setField(request, "refreshToken", "expired-token");
            given(refreshTokenRepository.findByToken("expired-token"))
                    .willReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED));
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("logout - refresh token deleted from DB")
        void logout_deletesRefreshToken() {
            authService.logout(1L);

            verify(refreshTokenRepository).deleteByUserId(1L);
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
                .name("tester")
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
