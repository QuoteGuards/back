package com.project.back.domain.training.repository;

import com.project.back.domain.training.entity.UserTrainingProgress;
import com.project.back.global.enums.TrainingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserTrainingProgressRepository extends JpaRepository<UserTrainingProgress, Long> {

    // 사용자 + 교육 콘텐츠로 진행 현황 조회
    Optional<UserTrainingProgress> findByUserIdAndTrainingContentId(Long userId, Long trainingContentId);

    // 사용자의 특정 상태 교육 목록 조회
    List<UserTrainingProgress> findByUserIdAndStatus(Long userId, TrainingStatus status);

    // 교육 이수 완료 여부 확인 (진도율 80% 이상 = COMPLETED)
    boolean existsByUserIdAndStatus(Long userId, TrainingStatus status);

    // 관리자용: 특정 교육 콘텐츠의 전체 사용자 이수 현황
    @Query("SELECT p FROM UserTrainingProgress p JOIN FETCH p.user WHERE p.trainingContent.id = :contentId")
    List<UserTrainingProgress> findAllByTrainingContentIdWithUser(@Param("contentId") Long contentId);
}
