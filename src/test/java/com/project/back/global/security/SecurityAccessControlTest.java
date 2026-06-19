package com.project.back.global.security;

import com.project.back.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL별 접근 제어(인증/인가) 통합 테스트
 *
 * @WebMvcTest 는 기본적으로 Spring Security 기본 자동 설정(CSRF 활성, Basic Auth)을 사용한다.
 * @Import 로 실제 SecurityConfig 및 의존 빈을 명시적으로 로드해야 우리의 필터 체인이 적용된다.
 * - SecurityConfig : @EnableWebSecurity 로 기본 자동 설정 비활성화 + 우리 규칙 적용
 * - JwtAuthenticationEntryPoint, JwtAccessDeniedHandler, SecurityErrorResponseWriter : 실제 401/403 JSON 응답 작성
 * - JwtTokenProvider 는 Mock → JWT 없는 요청은 인증 안 됨(미인증 테스트 시나리오 동작)
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponseWriter.class})
class SecurityAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // ============================================================
    // 1. 공개 엔드포인트 - 미인증 접근 허용
    // ============================================================

    @Nested
    @DisplayName("공개 엔드포인트 (/api/auth/**)")
    class PublicEndpoints {

        @Test
        @DisplayName("POST /api/auth/signup - 미인증 접근 허용")
        void signUp_noAuth_permitted() throws Exception {
            mockMvc.perform(post("/api/auth/signup"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // 401/403이 아니면 보안 통과 (400은 validation 실패, 허용됨)
                        assert status != 401 : "signup should not return 401";
                        assert status != 403 : "signup should not return 403";
                    });
        }

        @Test
        @DisplayName("POST /api/auth/login - 미인증 접근 허용")
        void login_noAuth_permitted() throws Exception {
            mockMvc.perform(post("/api/auth/login"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status != 401 : "login should not return 401";
                        assert status != 403 : "login should not return 403";
                    });
        }
    }

    // ============================================================
    // 2. 미인증 요청 → 401
    // ============================================================

    @Nested
    @DisplayName("미인증 요청 → 401")
    class UnauthenticatedRequests {

        @Test
        @DisplayName("미인증으로 /api/admin/users/** 접근 → 401")
        void adminUsers_noAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/admin/users/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("JWT_001"));
        }

        @Test
        @DisplayName("미인증으로 /api/admin/approval-requests/** 접근 → 401")
        void adminApproval_noAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/admin/approval-requests"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("JWT_001"));
        }

        @Test
        @DisplayName("미인증으로 /api/admin/dashboard/** 접근 → 401")
        void adminDashboard_noAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard/stats"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("JWT_001"));
        }

        @Test
        @DisplayName("미인증으로 /api/dashboard/me 접근 → 401")
        void dashboardMe_noAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/dashboard/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("JWT_001"));
        }
    }

    // ============================================================
    // 3. SUPER_ADMIN 전용 엔드포인트
    // ============================================================

    @Nested
    @DisplayName("/api/admin/users/** - SUPER_ADMIN 전용")
    class AdminUsersAccess {

        @Test
        @DisplayName("SALES_STAFF가 /api/admin/users/** 접근 → 403")
        @WithMockUser(roles = "SALES_STAFF")
        void adminUsers_salesStaff_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users/1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_007"));
        }

        @Test
        @DisplayName("SALES_MANAGER가 /api/admin/users/** 접근 → 403")
        @WithMockUser(roles = "SALES_MANAGER")
        void adminUsers_salesManager_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users/1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_007"));
        }

        @Test
        @DisplayName("SUPER_ADMIN이 /api/admin/users/** 접근 → 보안 통과")
        @WithMockUser(roles = "SUPER_ADMIN")
        void adminUsers_superAdmin_securityPassed() throws Exception {
            mockMvc.perform(get("/api/admin/users/1"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status != 401 : "SUPER_ADMIN should not get 401";
                        assert status != 403 : "SUPER_ADMIN should not get 403";
                    });
        }
    }

    // ============================================================
    // 4. SALES_MANAGER 이상 접근 엔드포인트
    // ============================================================

    @Nested
    @DisplayName("/api/admin/approval-requests/** - SALES_MANAGER, SUPER_ADMIN 접근 가능")
    class ApprovalRequestsAccess {

        @Test
        @DisplayName("SALES_STAFF가 /api/admin/approval-requests/** 접근 → 403")
        @WithMockUser(roles = "SALES_STAFF")
        void approvalRequests_salesStaff_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/approval-requests"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_007"));
        }

        @Test
        @DisplayName("SALES_MANAGER가 /api/admin/approval-requests/** 접근 → 보안 통과")
        @WithMockUser(roles = "SALES_MANAGER")
        void approvalRequests_salesManager_securityPassed() throws Exception {
            mockMvc.perform(get("/api/admin/approval-requests"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status != 401 : "SALES_MANAGER should not get 401";
                        assert status != 403 : "SALES_MANAGER should not get 403";
                    });
        }

        @Test
        @DisplayName("SUPER_ADMIN이 /api/admin/approval-requests/** 접근 → 보안 통과")
        @WithMockUser(roles = "SUPER_ADMIN")
        void approvalRequests_superAdmin_securityPassed() throws Exception {
            mockMvc.perform(get("/api/admin/approval-requests"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status != 401 && status != 403;
                    });
        }
    }

    @Nested
    @DisplayName("/api/admin/dashboard/** - SALES_MANAGER, SUPER_ADMIN 접근 가능")
    class AdminDashboardAccess {

        @Test
        @DisplayName("SALES_STAFF가 /api/admin/dashboard/** 접근 → 403")
        @WithMockUser(roles = "SALES_STAFF")
        void adminDashboard_salesStaff_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard/stats"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_007"));
        }

        @Test
        @DisplayName("SALES_MANAGER가 /api/admin/dashboard/** 접근 → 보안 통과")
        @WithMockUser(roles = "SALES_MANAGER")
        void adminDashboard_salesManager_securityPassed() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard/stats"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status != 401 && status != 403;
                    });
        }
    }

    // ============================================================
    // 5. 인증된 사용자 접근 엔드포인트
    // ============================================================

    @Nested
    @DisplayName("/api/dashboard/me - 인증된 사용자 접근 가능")
    class DashboardMeAccess {

        @Test
        @DisplayName("SALES_STAFF가 /api/dashboard/me 접근 → 보안 통과")
        @WithMockUser(roles = "SALES_STAFF")
        void dashboardMe_salesStaff_securityPassed() throws Exception {
            mockMvc.perform(get("/api/dashboard/me"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status != 401 : "authenticated user should not get 401";
                        assert status != 403 : "authenticated user should not get 403";
                    });
        }

        @Test
        @DisplayName("SALES_MANAGER가 /api/dashboard/me 접근 → 보안 통과")
        @WithMockUser(roles = "SALES_MANAGER")
        void dashboardMe_salesManager_securityPassed() throws Exception {
            mockMvc.perform(get("/api/dashboard/me"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status != 401 && status != 403;
                    });
        }
    }
}
