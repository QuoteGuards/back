package com.project.back.global.security;

import com.project.back.domain.approval.controller.ApprovalController;
import com.project.back.domain.approval.service.AiRiskSummaryService;
import com.project.back.domain.approval.service.ApprovalService;
import com.project.back.domain.auth.controller.AuthController;
import com.project.back.domain.auth.ratelimit.PasswordResetRateLimiter;
import com.project.back.domain.auth.service.AuthService;
import com.project.back.domain.auth.service.InitialPasswordSetupService;
import com.project.back.domain.auth.service.PasswordResetService;
import com.project.back.domain.user.controller.AdminUserController;
import com.project.back.domain.user.service.UserManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL별 접근 제어(인증/인가) 통합 테스트
 *
 * 인증 방식: {@code SecurityMockMvcRequestPostProcessors.jwt()}
 *
 * {@code @WithMockUser}는 HttpSession에 SecurityContext를 저장하지만,
 *      STATELESS 정책에서는 {@code RequestAttributeSecurityContextRepository}가 세션을 읽지 않아 인증이 전달되지 않는다.
 * {@code jwt()} 포스트 프로세서는 RequestAttribute에 직접 SecurityContext를 설정하므로 STATELESS에서 정상 동작한다.
 *
 * controllers를 명시하여 필요한 Controller만 로드한다.
 * /api/admin/dashboard/**, /api/dashboard/me는 컨트롤러 없이 SecurityConfig 규칙만으로 검증 가능하다.
 */
@WebMvcTest(controllers = {AuthController.class, AdminUserController.class, ApprovalController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponseWriter.class})
@TestPropertySource(properties = "cors.allowed-origin=https://quoteguard.n-e.kr,https://www.quoteguard.n-e.kr")
class SecurityAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private InitialPasswordSetupService initialPasswordSetupService;

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private ApprovalService approvalService;

    @MockitoBean
    private AiRiskSummaryService aiRiskSummaryService;

    @MockitoBean
    private PasswordResetRateLimiter passwordResetRateLimiter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    /**
     * 테스트용 인증 객체 생성 — principal을 Long userId로 설정하여 @AuthenticationPrincipal Long userId 호환
     */
    private RequestPostProcessor asUser(Long userId, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        return authentication(auth);
    }

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
                        // 401/403이 아니면 보안 통과 (400은 validation 실패, 허용됨)
                        int status = result.getResponse().getStatus();
                        assertThat(status).as("signup should not return 401").isNotEqualTo(401);
                        assertThat(status).as("signup should not return 403").isNotEqualTo(403);
                    });
        }

        @Test
        @DisplayName("POST /api/auth/login - 미인증 접근 허용")
        void login_noAuth_permitted() throws Exception {
            mockMvc.perform(post("/api/auth/login"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).as("login should not return 401").isNotEqualTo(401);
                        assertThat(status).as("login should not return 403").isNotEqualTo(403);
                    });
        }
    }

    // ============================================================
    // 1-1. CORS - 콤마로 구분된 여러 origin 허용 (www 서브도메인 403 회귀 방지)
    // ============================================================

    @Nested
    @DisplayName("CORS - cors.allowed-origin 다중 origin 지원")
    class CorsMultipleOrigins {

        @Test
        @DisplayName("apex 도메인 Origin으로 로그인 요청 → CORS 허용")
        void login_apexOrigin_corsAllowed() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .header("Origin", "https://quoteguard.n-e.kr"))
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://quoteguard.n-e.kr"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).as("허용된 origin은 CORS 차단(403)되지 않아야 함").isNotEqualTo(403);
                    });
        }

        @Test
        @DisplayName("www 서브도메인 Origin으로 로그인 요청 → CORS 허용 (Invalid CORS request 403 회귀 방지)")
        void login_wwwOrigin_corsAllowed() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .header("Origin", "https://www.quoteguard.n-e.kr"))
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://www.quoteguard.n-e.kr"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).as("허용된 origin은 CORS 차단(403)되지 않아야 함").isNotEqualTo(403);
                    });
        }

        @Test
        @DisplayName("허용 목록에 없는 origin → CORS 차단 403")
        void login_unknownOrigin_corsRejected() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .header("Origin", "https://evil.example.com"))
                    .andExpect(status().isForbidden());
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
        void adminUsers_salesStaff_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users/1")
                            .with(asUser(1L, "SALES_STAFF")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_006"));
        }

        @Test
        @DisplayName("SALES_MANAGER가 /api/admin/users/** 접근 → 403")
        void adminUsers_salesManager_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users/1")
                            .with(asUser(1L, "SALES_MANAGER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_006"));
        }

        @Test
        @DisplayName("SUPER_ADMIN이 /api/admin/users/** 접근 → 보안 통과")
        void adminUsers_superAdmin_securityPassed() throws Exception {
            mockMvc.perform(get("/api/admin/users/1")
                            .with(asUser(1L, "SUPER_ADMIN")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).as("SUPER_ADMIN should not get 401").isNotEqualTo(401);
                        assertThat(status).as("SUPER_ADMIN should not get 403").isNotEqualTo(403);
                    });
        }
    }

    // ============================================================
    // 4. SALES_MANAGER 이상 접근 엔드포인트
    // ============================================================

    @Nested
    @DisplayName("/api/admin/approval-requests/** - SUPER_ADMIN 전용 (SALES_MANAGER는 /api/manager/approval-requests 별도 사용)")
    class ApprovalRequestsAccess {

        @Test
        @DisplayName("SALES_STAFF가 /api/admin/approval-requests/** 접근 → 403")
        void approvalRequests_salesStaff_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/approval-requests")
                            .with(asUser(1L, "SALES_STAFF")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_006"));
        }

        @Test
        @DisplayName("SALES_MANAGER가 /api/admin/approval-requests/** 접근 → 403 (부서 스코프가 없는 admin 전용 API라 매니저는 /api/manager/approval-requests를 대신 사용)")
        void approvalRequests_salesManager_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/approval-requests")
                            .with(asUser(1L, "SALES_MANAGER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_006"));
        }

        @Test
        @DisplayName("SUPER_ADMIN이 /api/admin/approval-requests/** 접근 → 보안 통과")
        void approvalRequests_superAdmin_securityPassed() throws Exception {
            mockMvc.perform(get("/api/admin/approval-requests")
                            .with(asUser(1L, "SUPER_ADMIN")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).isNotEqualTo(401);
                        assertThat(status).isNotEqualTo(403);
                    });
        }
    }

    @Nested
    @DisplayName("/api/admin/dashboard/** - SALES_MANAGER, SUPER_ADMIN 접근 가능")
    class AdminDashboardAccess {

        @Test
        @DisplayName("SALES_STAFF가 /api/admin/dashboard/** 접근 → 403")
        void adminDashboard_salesStaff_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard/stats")
                            .with(asUser(1L, "SALES_STAFF")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("fail"))
                    .andExpect(jsonPath("$.code").value("AUTH_006"));
        }

        @Test
        @DisplayName("SALES_MANAGER가 /api/admin/dashboard/** 접근 → 보안 통과")
        void adminDashboard_salesManager_securityPassed() throws Exception {
            mockMvc.perform(get("/api/admin/dashboard/stats")
                            .with(asUser(1L, "SALES_MANAGER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).isNotEqualTo(401);
                        assertThat(status).isNotEqualTo(403);
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
        void dashboardMe_salesStaff_securityPassed() throws Exception {
            mockMvc.perform(get("/api/dashboard/me")
                            .with(asUser(1L, "SALES_STAFF")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).as("authenticated user should not get 401").isNotEqualTo(401);
                        assertThat(status).as("authenticated user should not get 403").isNotEqualTo(403);
                    });
        }

        @Test
        @DisplayName("SALES_MANAGER가 /api/dashboard/me 접근 → 보안 통과")
        void dashboardMe_salesManager_securityPassed() throws Exception {
            mockMvc.perform(get("/api/dashboard/me")
                            .with(asUser(1L, "SALES_MANAGER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).isNotEqualTo(401);
                        assertThat(status).isNotEqualTo(403);
                    });
        }
    }
}
