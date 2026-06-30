package com.project.back.domain.auth.repository;

import com.project.back.domain.auth.entity.PasswordResetToken;
import com.project.back.domain.auth.entity.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * 사용자의 특정 목적 미사용·미만료 토큰을 즉시 만료 처리한다.
     * 새 토큰 발급 전에 호출해 중복 유효 토큰을 방지한다.
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.expiresAt = :now
            WHERE t.userId = :userId
              AND t.purpose = :purpose
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
            """)
    void expireAllActiveByUserIdAndPurpose(
            @Param("userId") Long userId,
            @Param("purpose") TokenPurpose purpose,
            @Param("now") LocalDateTime now
    );

    /**
     * 기존 호환성 유지 (PASSWORD_RESET 전용)
     */
    default void expireAllActiveByUserId(Long userId, LocalDateTime now) {
        expireAllActiveByUserIdAndPurpose(userId, TokenPurpose.PASSWORD_RESET, now);
    }

    /**
     * 미사용·미만료 토큰을 원자적으로 사용 처리한다.
     *
     * <p>purpose 조건을 포함해 다른 목적의 토큰을 잘못 소모하는 것을 방지한다.
     * 조건부 UPDATE 한 건으로 검증과 상태 변경을 동시에 처리해
     * 동시 요청에서 둘 이상이 성공하는 것을 방지한다.</p>
     *
     * @return 업데이트된 행 수 (1이면 성공, 0이면 이미 사용됐거나 만료됐거나 purpose 불일치)
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.tokenHash = :tokenHash
              AND t.purpose = :purpose
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
            """)
    int markUsedIfValid(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") TokenPurpose purpose,
            @Param("now") LocalDateTime now
    );

    /**
     * 특정 사용자·목적의 가장 최근 생성 토큰 조회 (재발송 쿨다운 확인용)
     * Spring Data JPA 파생 쿼리로 JPQL LIMIT 의존을 제거한다.
     */
    Optional<PasswordResetToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(
            Long userId,
            TokenPurpose purpose
    );
}
