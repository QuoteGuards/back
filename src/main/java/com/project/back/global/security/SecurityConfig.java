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

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

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
                        .requestMatchers("/api/auth/signup", "/api/auth/login").permitAll()

                        // 사용자 통계 조회 (본인)
                        .requestMatchers(HttpMethod.GET, "/api/users/me/stats").authenticated()

                        // 관리자 사용자별 통계 조회 (SALES_MANAGER, SUPER_ADMIN) - 더 넓은 /api/admin/users/** 규칙보다 먼저 등록
                        .requestMatchers(HttpMethod.GET, "/api/admin/users/*/stats").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")

                        .requestMatchers("/api/admin/users/**").hasRole("SUPER_ADMIN")

                        // 승인 요청 (영업사원만)
                        .requestMatchers(HttpMethod.POST, "/api/quotes/*/approval-requests").hasRole("SALES_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/quotes/*/resubmit").hasRole("SALES_STAFF")

                        // 승인 이력/사유 조회 (인증된 사용자 전체)
                        .requestMatchers(HttpMethod.GET, "/api/quotes/*/approval-histories").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/quotes/*/approval-reasons").authenticated()

                        // 관리자 전용
                        .requestMatchers("/api/admin/approval-requests/**").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/quotes/*/approve").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/quotes/*/reject").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                        .requestMatchers("/api/admin/users/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/dashboard/**").hasAnyRole("SALES_MANAGER", "SUPER_ADMIN")
                            // 제품, 카테고리 관리는 관리자만
                        .requestMatchers("/api/admin/categories/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/products/**").hasRole("SUPER_ADMIN")
                            // 할인정책관리도 관리자만 가능
                        .requestMatchers("/api/admin/discounts/**").hasRole("SUPER_ADMIN")
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
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
