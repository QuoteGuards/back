package com.project.back.domain.auth.controller;

import tools.jackson.databind.json.JsonMapper;
import com.project.back.domain.auth.dto.response.LoginResponse;
import com.project.back.domain.auth.dto.response.TokenRefreshResponse;
import com.project.back.domain.auth.service.AuthService;
import com.project.back.domain.user.repository.UserRepository;
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

import com.project.back.global.security.JwtTokenProvider;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
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

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /api/auth/login - 200 OK (success, mustChangePassword=false)")
    @WithMockUser
    void login_success() throws Exception {
        given(authService.login(any()))
                .willReturn(LoginResponse.of("mock.jwt.token", "mock.refresh.token", false));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "2026001@quoteguard.com",
                                "password", "QG-ABCD1234"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("mock.refresh.token"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/login - 200 OK (mustChangePassword=true)")
    @WithMockUser
    void login_mustChangePassword() throws Exception {
        given(authService.login(any()))
                .willReturn(LoginResponse.of("mock.jwt.token", "mock.refresh.token", true));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "2026001@quoteguard.com",
                                "password", "QG-ABCD1234"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/login - 400 Bad Request (invalid email format)")
    @WithMockUser
    void login_invalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "not-an-email",
                                "password", "Pass@1234"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 403 Forbidden (SUSPENDED user)")
    @WithMockUser
    void login_suspendedUser() throws Exception {
        given(authService.login(any())).willThrow(new CustomException(ErrorCode.USER_SUSPENDED));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "2026001@quoteguard.com",
                                "password", "Pass@1234"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 200 OK (new access token issued)")
    @WithMockUser
    void refresh_success() throws Exception {
        given(authService.refresh(any()))
                .willReturn(TokenRefreshResponse.of("new.access.token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", "valid-refresh-token"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("new.access.token"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 401 (refresh token not found)")
    @WithMockUser
    void refresh_tokenNotFound() throws Exception {
        given(authService.refresh(any()))
                .willThrow(new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", "invalid-token"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("JWT_003"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 400 (blank refreshToken)")
    @WithMockUser
    void refresh_blankToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    @DisplayName("POST /api/auth/logout - 200 OK (userId=1L principal verified)")
    void logout_success() throws Exception {
        String token = jwtTokenProvider.createAccessToken(1L, "2026001@quoteguard.com", "SALES_STAFF");
        willDoNothing().given(authService).logout(1L);

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(authService).logout(1L);
    }
}
