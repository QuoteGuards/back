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

    /**
     * 미사용·미만료 토큰을 원자적으로 사용 처리한다.
     *
     * <p>조건부 UPDATE 한 건으로 검증과 상태 변경을 동시에 처리해
     * 동시 요청에서 둘 이상이 성공하는 것을 방지한다.</p>
     *
     * @return 업데이트된 행 수 (1이면 성공, 0이면 이미 사용됐거나 만료됨)
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.tokenHash = :tokenHash
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
            """)
    int markUsedIfValid(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
