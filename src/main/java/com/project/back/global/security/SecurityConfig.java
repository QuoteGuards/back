package com.project.back.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // 프론트가 apex(quoteguard.n-e.kr)와 www(www.quoteguard.n-e.kr) 두 호스트 모두에서
    // 서비스되므로, 콤마로 구분된 여러 origin을 허용해야 한다. Spring의 CORS 필터는
    // Origin 헤더가 있는 모든 요청을 CORS 요청으로 간주해 이 목록과 대조하므로,
    // 목록에 없는 origin은 리버스 프록시를 거친 동일 서비스 도메인이어도 403(Invalid CORS request)으로 차단된다.
    @Value("${cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm",
                                "/api/auth/set-initial-password"
                        ).permitAll()

                        // 업로드된 이미지는 인증 없이 접근 (img src 용)
                        .requestMatchers("/uploads/**").permitAll()

                        // SSE 구독은 EventSource가 헤더를 못 실으므로 단기 SSE 토큰으로 자체 인증 (permitAll)
                        .requestMatchers(HttpMethod.GET, "/api/notifications/subscribe").permitAll()

                        // 사용자 통계 조회 (본인)
                        .requestMatchers(HttpMethod.GET, "/api/users/me/stats").authenticated()

                        // 관리자 사용자별 통계 조회 (SALES_MANAGER, SUPER_ADMIN) - 더 넓은 /api/admin/users/** 규칙보다 먼저 등록
                        .requestMatchers(HttpMethod.GET, "/api/admin/users/*/stats").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")

                        .requestMatchers("/api/admin/users/**").hasRole("SUPER_ADMIN")

                        // 승인 요청 (영업사원 + 영업관리자)
                        .requestMatchers(HttpMethod.POST, "/api/quotes/*/approval-requests").hasAnyRole("SALES_STAFF", "SALES_MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/quotes/*/approval-requests/*/memo").hasRole("SALES_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/quotes/*/resubmit").hasAnyRole("SALES_STAFF", "SALES_MANAGER")

                        // 승인 이력/사유 조회 (인증된 사용자 전체)
                        .requestMatchers(HttpMethod.GET, "/api/quotes/*/approval-histories").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/quotes/*/approval-reasons").authenticated()

                        // 관리자 전용
                        .requestMatchers(HttpMethod.GET, "/api/admin/quotes").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/manager/quotes").hasRole("SALES_MANAGER")
                        .requestMatchers("/api/admin/approval-requests/**").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/quotes/*/approve").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/quotes/*/reject").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/quotes/*/ai-summary").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/users/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/dashboard/**").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                            // 제품, 카테고리 관리는 관리자만
                        .requestMatchers("/api/admin/categories/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/products/**").hasRole("SUPER_ADMIN")
                            // 할인정책관리도 관리자만 가능
                        .requestMatchers("/api/admin/discounts/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/admin/trainings/status").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/trainings/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/dashboard/me").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, jwtAuthenticationEntryPoint),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = Arrays.stream(allowedOrigin.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
