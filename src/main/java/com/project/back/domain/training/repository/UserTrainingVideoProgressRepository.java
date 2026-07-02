package com.project.back.domain.training.repository;

import com.project.back.domain.training.entity.UserTrainingVideoProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserTrainingVideoProgressRepository extends JpaRepository<UserTrainingVideoProgress, Long> {

    Optional<UserTrainingVideoProgress> findByUserIdAndTrainingVideoId(Long userId, Long trainingVideoId);

    @Query("""
            select p from UserTrainingVideoProgress p
            join fetch p.trainingVideo v
            where p.user.id = :userId and v.trainingContent.id = :contentId
            """)
    List<UserTrainingVideoProgress> findAllByUserIdAndContentId(
            @Param("userId") Long userId,
            @Param("contentId") Long contentId
    );

    @Query("""
            select p from UserTrainingVideoProgress p
            join fetch p.user u
            join fetch p.trainingVideo v
            where v.id in :videoIds
            """)
    List<UserTrainingVideoProgress> findAllByTrainingVideoIdIn(@Param("videoIds") Collection<Long> videoIds);
}
