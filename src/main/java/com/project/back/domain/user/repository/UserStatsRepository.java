package com.project.back.domain.user.repository;

import com.project.back.domain.user.entity.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserStatsRepository extends JpaRepository<UserStats, Long> {

    @Query("SELECT us FROM UserStats us JOIN FETCH us.user WHERE us.user.id = :userId")
    Optional<UserStats> findByUserId(@Param("userId") Long userId);
}
