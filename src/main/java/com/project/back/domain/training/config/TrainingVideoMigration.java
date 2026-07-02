package com.project.back.domain.training.config;

import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.domain.training.entity.TrainingVideo;
import com.project.back.domain.training.entity.UserTrainingProgress;
import com.project.back.domain.training.entity.UserTrainingVideoProgress;
import com.project.back.domain.training.repository.TrainingContentRepository;
import com.project.back.domain.training.repository.TrainingVideoRepository;
import com.project.back.domain.training.repository.UserTrainingProgressRepository;
import com.project.back.domain.training.repository.UserTrainingVideoProgressRepository;
import com.project.back.global.enums.TrainingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainingVideoMigration {

    private final TrainingContentRepository trainingContentRepository;
    private final TrainingVideoRepository trainingVideoRepository;
    private final UserTrainingProgressRepository userTrainingProgressRepository;
    private final UserTrainingVideoProgressRepository userTrainingVideoProgressRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyVideoUrl() {
        Optional<TrainingContent> contentOpt = trainingContentRepository
                .findFirstByTrainingTypeAndActiveTrueOrderByUpdatedAtDesc(TrainingType.QUOTE_WRITE);
        if (contentOpt.isEmpty()) {
            return;
        }

        TrainingContent content = contentOpt.get();
        if (trainingVideoRepository.existsByTrainingContentId(content.getId())) {
            return;
        }

        if (content.getVideoUrl() == null || content.getVideoUrl().isBlank()) {
            return;
        }

        TrainingVideo migratedVideo = trainingVideoRepository.save(TrainingVideo.builder()
                .trainingContent(content)
                .title("견적 작성 가이드 영상")
                .videoUrl(content.getVideoUrl())
                .sortOrder(1)
                .active(true)
                .build());

        List<UserTrainingProgress> legacyProgressList = userTrainingProgressRepository
                .findAllByTrainingContentIdWithUser(content.getId());

        for (UserTrainingProgress legacy : legacyProgressList) {
            userTrainingVideoProgressRepository.save(UserTrainingVideoProgress.builder()
                    .user(legacy.getUser())
                    .trainingVideo(migratedVideo)
                    .progressRate(legacy.getProgressRate())
                    .status(legacy.getStatus())
                    .watchedSeconds(legacy.getWatchedSeconds())
                    .lastWatchedSeconds(legacy.getLastWatchedSeconds())
                    .completedAt(legacy.getCompletedAt())
                    .build());
        }

        log.info("Migrated legacy training video_url to training_videos for contentId={}", content.getId());
    }
}
