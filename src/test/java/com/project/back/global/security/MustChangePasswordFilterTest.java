package com.project.back.global.security;

import com.project.back.domain.user.controller.MyProfileController;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.service.MyProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MustChangePasswordFilter 단위 테스트
 *
 * <ul>
 *   <li>mustChangePassword=true 사용자 → PATCH /api/users/me/password 외 모든 요청 403 차단</li>
 *   <li>mustChangePassword=true 사용자 → PATCH /api/users/me/password 통과</li>
 *   <li>mustChangePassword=false 사용자 → 모든 요청 통과</li>
 * </ul>
 */
@WebMvcTest(controllers = {MyProfileController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        SecurityErrorResponseWriter.class})
class MustChangePasswordFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MyProfileService myProfileService;

    private RequestPostProcessor asUser(Long userId, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        return authentication(auth);
    }

    private User buildUser(boolean mustChangePassword) {
        return User.builder()
                .memberNumber("2600001")
                .email("2600001@quoteguard.com")
                .password("encoded")
                .name("홍길동")
                .role(UserRole.SALES_STAFF)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(mustChangePassword)
                .build();
    }

    // ── mustChangePassword=true 차단 ──────────────────────────────────────

    @Nested
    @DisplayName("mustChangePassword=true - 비밀번호 변경 엔드포인트 외 차단")
    class BlockedWhenMustChange {

        @Test
        @DisplayName("PATCH /api/users/me - 403 MUST_CHANGE_PASSWORD 반환")
        void patchProfile_blocked() throws Exception {
            given(userRepository.findById(1L)).willReturn(Optional.of(buildUser(true)));

            mockMvc.perform(patch("/api/users/me")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "홍길동"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_007"));
        }
    }

    // ── mustChangePassword=true 이지만 비밀번호 변경 통과 ─────────────────

    @Nested
    @DisplayName("mustChangePassword=true - PATCH /api/users/me/password 통과")
    class AllowedPasswordChange {

        @Test
        @DisplayName("PATCH /api/users/me/password - 필터 통과 (403 아님)")
        void patchPassword_allowed() throws Exception {
            given(userRepository.findById(1L)).willReturn(Optional.of(buildUser(true)));

            mockMvc.perform(patch("/api/users/me/password")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "OldPass@1",
                                    "newPassword", "NewPass@1",
                                    "newPasswordConfirm", "NewPass@1"
                            ))))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus())
                                    .as("비밀번호 변경 엔드포인트는 필터를 통과해야 한다")
                                    .isNotEqualTo(403));
        }
    }

    // ── mustChangePassword=false 통과 ─────────────────────────────────────

    @Nested
    @DisplayName("mustChangePassword=false - 모든 요청 통과")
    class AllowedWhenNotMustChange {

        @Test
        @DisplayName("PATCH /api/users/me - 필터 통과 (403 아님)")
        void patchProfile_allowed() throws Exception {
            given(userRepository.findById(1L)).willReturn(Optional.of(buildUser(false)));

            mockMvc.perform(patch("/api/users/me")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "홍길동"))))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus())
                                    .as("mustChangePassword=false 사용자는 필터를 통과해야 한다")
                                    .isNotEqualTo(403));
        }

        @Test
        @DisplayName("PATCH /api/users/me/password - 필터 통과 (403 아님)")
        void patchPassword_allowed() throws Exception {
            given(userRepository.findById(1L)).willReturn(Optional.of(buildUser(false)));

            mockMvc.perform(patch("/api/users/me/password")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "OldPass@1",
                                    "newPassword", "NewPass@1",
                                    "newPasswordConfirm", "NewPass@1"
                            ))))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus())
                                    .as("mustChangePassword=false 사용자는 필터를 통과해야 한다")
                                    .isNotEqualTo(403));
        }
    }

    // ── 미인증 요청 통과 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("미인증 요청 - 필터 통과 (Spring Security가 401 처리)")
    class UnauthenticatedPassThrough {

        @Test
        @DisplayName("미인증 PATCH /api/users/me - 401 반환 (필터 미개입)")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/users/me")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "홍길동"))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
