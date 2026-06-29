package com.project.back.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 원본 토큰의 SHA-256 해시값만 저장한다.
     * 원본 토큰은 이메일 링크에만 포함하고 DB에는 보관하지 않는다.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * 토큰 용도: PASSWORD_RESET(비밀번호 재설정) 또는 INITIAL_PASSWORD_SETUP(초기 비밀번호 설정)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private TokenPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static PasswordResetToken of(Long userId, String tokenHash, LocalDateTime expiresAt) {
        return PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .purpose(TokenPurpose.PASSWORD_RESET)
                .expiresAt(expiresAt)
                .build();
    }

    public static PasswordResetToken ofInitialSetup(Long userId, String tokenHash, LocalDateTime expiresAt) {
        return PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .purpose(TokenPurpose.INITIAL_PASSWORD_SETUP)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed() {
        this.usedAt = LocalDateTime.now();
    }
}
