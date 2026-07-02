package com.project.back.domain.training.config;

import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.domain.training.entity.TrainingVideo;
import com.project.back.domain.training.repository.TrainingContentRepository;
import com.project.back.domain.training.repository.TrainingVideoRepository;
import com.project.back.global.enums.TrainingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainingVideoMigration {

    private static final int LEGACY_MIGRATION_SORT_ORDER = 1;

    private final TrainingContentRepository trainingContentRepository;
    private final TrainingVideoRepository trainingVideoRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyVideoUrl() {
        Optional<TrainingContent> contentOpt = trainingContentRepository
                .findFirstByTrainingTypeAndActiveTrueOrderByUpdatedAtDesc(TrainingType.QUOTE_WRITE);
        if (contentOpt.isEmpty()) {
            return;
        }

        TrainingContent content = contentOpt.get();
        if (trainingVideoRepository
                .findByTrainingContentIdAndSortOrder(content.getId(), LEGACY_MIGRATION_SORT_ORDER)
                .isPresent()) {
            return;
        }

        if (content.getVideoUrl() == null || content.getVideoUrl().isBlank()) {
            return;
        }

        try {
            trainingVideoRepository.save(TrainingVideo.builder()
                    .trainingContent(content)
                    .title("견적 작성 가이드 영상")
                    .videoUrl(content.getVideoUrl())
                    .sortOrder(LEGACY_MIGRATION_SORT_ORDER)
                    .active(true)
                    .build());
            log.info("Migrated legacy training video_url to training_videos for contentId={}", content.getId());
        } catch (DataIntegrityViolationException e) {
            log.debug("Legacy training video already migrated for contentId={}", content.getId());
        }
    }
}
