package com.project.back.domain.user.repository;

import com.project.back.domain.user.entity.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserStatsRepository extends JpaRepository<UserStats, Long> {

    @Query("SELECT us FROM UserStats us JOIN FETCH us.user WHERE us.user.id = :userId")
    Optional<UserStats> findByUserId(@Param("userId") Long userId);

    /** 배치 재집계 대상 산출: 이미 통계 행이 존재하는 사용자 ID 목록 조회 */
    @Query("SELECT us.user.id FROM UserStats us")
    List<Long> findAllUserIds();
}
