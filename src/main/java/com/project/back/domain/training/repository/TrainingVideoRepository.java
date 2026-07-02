package com.project.back.domain.training.repository;

import com.project.back.domain.training.entity.TrainingVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingVideoRepository extends JpaRepository<TrainingVideo, Long> {

    List<TrainingVideo> findByTrainingContentIdOrderBySortOrderAscIdAsc(Long trainingContentId);

    List<TrainingVideo> findByTrainingContentIdAndActiveTrueOrderBySortOrderAscIdAsc(Long trainingContentId);

    Optional<TrainingVideo> findByIdAndTrainingContentId(Long id, Long trainingContentId);

    Optional<TrainingVideo> findByTrainingContentIdAndSortOrder(Long trainingContentId, int sortOrder);

    boolean existsByTrainingContentId(Long trainingContentId);
}
