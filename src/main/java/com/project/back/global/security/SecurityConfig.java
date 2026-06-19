package com.project.back.global.security;

import lombok.RequiredArgsConstructor;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

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
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()

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
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
