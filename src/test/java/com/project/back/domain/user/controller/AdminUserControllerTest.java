package com.project.back.domain.user.controller;

import tools.jackson.databind.json.JsonMapper;
import com.project.back.domain.auth.service.InitialPasswordSetupService;
import com.project.back.domain.user.dto.response.AdminCreateUserResponse;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.domain.user.service.UserManagementService;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.security.JwtAccessDeniedHandler;
import com.project.back.global.security.JwtAuthenticationEntryPoint;
import com.project.back.global.security.JwtTokenProvider;
import com.project.back.global.security.SecurityConfig;
import com.project.back.global.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponseWriter.class})
@DisplayName("POST /api/admin/users - 관리자 계정 생성 API 테스트")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private InitialPasswordSetupService initialPasswordSetupService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void stubUserRepository() {
        User activeUser = User.builder()
                .memberNumber("2600001")
                .email("2600001@quoteguard.com")
                .password("encoded")
                .name("테스트")
                .role(UserRole.SALES_STAFF)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build();
        given(userRepository.findById(anyLong())).willReturn(Optional.of(activeUser));
    }

    private RequestPostProcessor asUser(Long userId, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        return authentication(auth);
    }

    // ── 성공 케이스 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("SUPER_ADMIN - 계정 생성 성공 시 201 Created + passwordInitialized=false 반환")
        void createUser_superAdmin_success() throws Exception {
            AdminCreateUserResponse mockResponse = AdminCreateUserResponse.builder()
                    .id(1L)
                    .memberNumber("2026001")
                    .email("2026001@quoteguard.com")
                    .name("홍길동")
                    .department("영업1팀")
                    .position("대리")
                    .role(UserRole.SALES_STAFF.name())
                    .status(UserStatus.ACTIVE.name())
                    .passwordInitialized(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            given(userManagementService.createUser(any())).willReturn(mockResponse);

            mockMvc.perform(post("/api/admin/users")
                            .with(asUser(1L, "SUPER_ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "홍길동",
                                    "department", "영업1팀",
                                    "position", "대리",
                                    "phone", "010-1234-5678",
                                    "role", "SALES_STAFF"
                            ))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.memberNumber").value("2026001"))
                    .andExpect(jsonPath("$.data.email").value("2026001@quoteguard.com"))
                    .andExpect(jsonPath("$.data.passwordInitialized").value(false))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }
    }

    // ── 접근 제어 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("접근 제어 - 권한 없는 사용자")
    class AccessControl {

        @Test
        @DisplayName("SALES_STAFF - 계정 생성 API 접근 시 403 Forbidden")
        void createUser_salesStaff_forbidden() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .with(asUser(1L, "SALES_STAFF"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "홍길동",
                                    "role", "SALES_STAFF"
                            ))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SALES_MANAGER - 계정 생성 API 접근 시 403 Forbidden")
        void createUser_salesManager_forbidden() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .with(asUser(1L, "SALES_MANAGER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "홍길동",
                                    "role", "SALES_STAFF"
                            ))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("인증 없음 - 계정 생성 API 접근 시 401 Unauthorized")
        void createUser_unauthenticated_unauthorized() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "홍길동",
                                    "role", "SALES_STAFF"
                            ))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 유효성 검사 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("입력 유효성 검사")
    class Validation {

        @Test
        @DisplayName("이름 누락 시 400 Bad Request")
        void createUser_missingName_badRequest() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .with(asUser(1L, "SUPER_ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "role", "SALES_STAFF"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"));
        }

        @Test
        @DisplayName("권한 누락 시 400 Bad Request")
        void createUser_missingRole_badRequest() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .with(asUser(1L, "SUPER_ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "홍길동"
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("fail"));
        }

        @Test
        @DisplayName("이메일 중복(자동 생성 충돌) 시 409 Conflict")
        void createUser_duplicateEmail_conflict() throws Exception {
            given(userManagementService.createUser(any()))
                    .willThrow(new CustomException(ErrorCode.DUPLICATE_EMAIL));

            mockMvc.perform(post("/api/admin/users")
                            .with(asUser(1L, "SUPER_ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "홍길동",
                                    "phone", "010-1234-5678",
                                    "role", "SALES_STAFF"
                            ))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUTH_001"));
        }
    }
}
