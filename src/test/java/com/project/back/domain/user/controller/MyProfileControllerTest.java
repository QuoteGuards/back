package com.project.back.domain.user.controller;

import tools.jackson.databind.json.JsonMapper;
import com.project.back.domain.user.dto.response.MyProfileResponse;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.service.MyProfileService;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.global.security.JwtAccessDeniedHandler;
import com.project.back.global.security.JwtAuthenticationEntryPoint;
import com.project.back.global.security.JwtTokenProvider;
import com.project.back.global.security.SecurityConfig;
import com.project.back.global.security.SecurityErrorResponseWriter;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MyProfileController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponseWriter.class})
class MyProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private MyProfileService myProfileService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private RequestPostProcessor asUser(Long userId, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        return authentication(auth);
    }

    private MyProfileResponse buildProfileResponse() {
        return MyProfileResponse.builder()
                .id(1L)
                .email("user@test.com")
                .name("홍길동")
                .department("영업1팀")
                .position("대리")
                .phone("010-1234-5678")
                .role(UserRole.SALES_STAFF.name())
                .status(UserStatus.ACTIVE.name())
                .lastLoginAt(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── 프로필 수정 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/users/me - 프로필 수정")
    class UpdateMyProfile {

        @Test
        @DisplayName("이름과 전화번호 수정 성공 - 200 OK")
        void updateMyProfile_success() throws Exception {
            given(myProfileService.updateMyProfile(eq(1L), any())).willReturn(buildProfileResponse());

            mockMvc.perform(patch("/api/users/me")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "홍길동",
                                    "phone", "010-1234-5678"
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.name").value("홍길동"))
                    .andExpect(jsonPath("$.data.email").value("user@test.com"))
                    .andExpect(jsonPath("$.data.role").value("SALES_STAFF"));
        }

        @Test
        @DisplayName("인증되지 않은 사용자 접근 - 401 Unauthorized")
        void updateMyProfile_unauthenticated() throws Exception {
            mockMvc.perform(patch("/api/users/me")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "테스터"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("잘못된 전화번호 형식 - 400 Bad Request")
        void updateMyProfile_invalidPhone() throws Exception {
            mockMvc.perform(patch("/api/users/me")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "01012345678"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"));
        }

        @Test
        @DisplayName("50자 초과 이름 - 400 Bad Request")
        void updateMyProfile_nameTooLong() throws Exception {
            mockMvc.perform(patch("/api/users/me")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "가".repeat(51)
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"));
        }

        @Test
        @DisplayName("중복 전화번호 - 409 Conflict")
        void updateMyProfile_duplicatePhone() throws Exception {
            given(myProfileService.updateMyProfile(eq(1L), any()))
                    .willThrow(new CustomException(ErrorCode.DUPLICATE_PHONE));

            mockMvc.perform(patch("/api/users/me")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", "010-7777-8888"
                            ))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("USER_004"));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 - 404 Not Found")
        void updateMyProfile_userNotFound() throws Exception {
            given(myProfileService.updateMyProfile(eq(1L), any()))
                    .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(patch("/api/users/me")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "테스터"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_002"));
        }

        @Test
        @DisplayName("응답 DTO에 내부 관리자 필드(승인/정지 이력) 미포함 확인")
        void updateMyProfile_noInternalAdminFields() throws Exception {
            given(myProfileService.updateMyProfile(eq(1L), any())).willReturn(buildProfileResponse());

            mockMvc.perform(patch("/api/users/me")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", "테스터"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.approvedBy").doesNotExist())
                    .andExpect(jsonPath("$.data.rejectedAt").doesNotExist())
                    .andExpect(jsonPath("$.data.suspendedBy").doesNotExist())
                    .andExpect(jsonPath("$.data.rejectReason").doesNotExist());
        }
    }

    // ── 비밀번호 변경 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/users/me/password - 비밀번호 변경")
    class ChangeMyPassword {

        @Test
        @DisplayName("비밀번호 변경 성공 - 200 OK")
        void changeMyPassword_success() throws Exception {
            mockMvc.perform(patch("/api/users/me/password")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "OldPass@1",
                                    "newPassword", "NewPass@1",
                                    "newPasswordConfirm", "NewPass@1"
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("인증되지 않은 사용자 접근 - 401 Unauthorized")
        void changeMyPassword_unauthenticated() throws Exception {
            mockMvc.perform(patch("/api/users/me/password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "OldPass@1",
                                    "newPassword", "NewPass@1",
                                    "newPasswordConfirm", "NewPass@1"
                            ))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("현재 비밀번호 불일치 - 401 Unauthorized")
        void changeMyPassword_wrongCurrent() throws Exception {
            doThrow(new CustomException(ErrorCode.INVALID_PASSWORD))
                    .when(myProfileService).changeMyPassword(eq(1L), any());

            mockMvc.perform(patch("/api/users/me/password")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "WrongPass@1",
                                    "newPassword", "NewPass@1",
                                    "newPasswordConfirm", "NewPass@1"
                            ))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_003"));
        }

        @Test
        @DisplayName("새 비밀번호 확인 불일치 - 400 Bad Request")
        void changeMyPassword_confirmMismatch() throws Exception {
            doThrow(new CustomException(ErrorCode.PASSWORD_CONFIRM_MISMATCH))
                    .when(myProfileService).changeMyPassword(eq(1L), any());

            mockMvc.perform(patch("/api/users/me/password")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "OldPass@1",
                                    "newPassword", "NewPass@1",
                                    "newPasswordConfirm", "DifferentPass@1"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("USER_006"));
        }

        @Test
        @DisplayName("현재 비밀번호와 동일한 새 비밀번호 - 400 Bad Request")
        void changeMyPassword_sameAsCurrent() throws Exception {
            doThrow(new CustomException(ErrorCode.SAME_AS_CURRENT_PASSWORD))
                    .when(myProfileService).changeMyPassword(eq(1L), any());

            mockMvc.perform(patch("/api/users/me/password")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "OldPass@1",
                                    "newPassword", "OldPass@1",
                                    "newPasswordConfirm", "OldPass@1"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("USER_005"));
        }

        @Test
        @DisplayName("비밀번호 정책 위반 (8자 미만, 특수문자 없음) - 400 Bad Request")
        void changeMyPassword_policyViolation() throws Exception {
            mockMvc.perform(patch("/api/users/me/password")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "OldPass@1",
                                    "newPassword", "short",
                                    "newPasswordConfirm", "short"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"));
        }

        @Test
        @DisplayName("필수 필드 누락 - 400 Bad Request")
        void changeMyPassword_missingFields() throws Exception {
            mockMvc.perform(patch("/api/users/me/password")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", "OldPass@1"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"));
        }
    }
}
