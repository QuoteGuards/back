package com.project.back.domain.training.repository;

import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.global.enums.TrainingType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainingContentRepository extends JpaRepository<TrainingContent, Long> {

    // 견적 작성 화면 진입 시 활성화된 필수 교육 콘텐츠 조회
    Optional<TrainingContent> findByTrainingTypeAndActiveTrue(TrainingType trainingType);
}
