package com.project.back.domain.auth.repository;

import com.project.back.domain.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * 사용자의 미사용·미만료 토큰을 즉시 만료 처리한다.
     * 새 토큰 발급 전에 호출해 중복 유효 토큰을 방지한다.
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.expiresAt = :now
            WHERE t.userId = :userId
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
            """)
    void expireAllActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
