package com.project.back.domain.auth.controller;

import tools.jackson.databind.json.JsonMapper;
import com.project.back.domain.auth.dto.response.LoginResponse;
import com.project.back.domain.auth.dto.response.SignUpResponse;
import com.project.back.domain.auth.service.AuthService;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserRole;
import com.project.back.domain.user.entity.UserStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/signup - 201 Created")
    @WithMockUser
    void signUp_success() throws Exception {
        User mockUser = User.builder().email("new@example.com").password("encoded").name("홍길동").build();
        given(authService.signUp(any())).willReturn(SignUpResponse.from(mockUser));

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "new@example.com",
                                "password", "Pass@1234",
                                "name", "홍길동"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.role").value(UserRole.SALES_STAFF.name()))
                .andExpect(jsonPath("$.data.status").value(UserStatus.PENDING.name()));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 400 Bad Request (validation 실패)")
    @WithMockUser
    void signUp_validationFail() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "invalid-email",
                                "password", "short",
                                "name", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 409 Conflict (중복 이메일)")
    @WithMockUser
    void signUp_duplicateEmail() throws Exception {
        given(authService.signUp(any())).willThrow(new CustomException(ErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "exist@example.com",
                                "password", "Pass@1234",
                                "name", "홍길동"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 200 OK (로그인 성공)")
    @WithMockUser
    void login_success() throws Exception {
        given(authService.login(any())).willReturn(LoginResponse.of("mock.jwt.token"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "approved@example.com",
                                "password", "Pass@1234"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("mock.jwt.token"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 403 Forbidden (PENDING 사용자)")
    @WithMockUser
    void login_pendingUser() throws Exception {
        given(authService.login(any())).willThrow(new CustomException(ErrorCode.USER_PENDING));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "pending@example.com",
                                "password", "Pass@1234"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }
}
