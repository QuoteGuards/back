package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.entity.TrainingVideo;
import com.project.back.domain.training.entity.UserTrainingVideoProgress;
import com.project.back.global.enums.TrainingStatus;

import java.math.BigDecimal;

public record TrainingVideoResponse(
        Long id,
        String title,
        String videoUrl,
        int sortOrder,
        boolean active,
        TrainingStatus status,
        BigDecimal progressRate,
        int watchedSeconds,
        int lastWatchedSeconds
) {
    public static TrainingVideoResponse forAdmin(TrainingVideo video) {
        return new TrainingVideoResponse(
                video.getId(),
                video.getTitle(),
                video.getVideoUrl(),
                video.getSortOrder(),
                video.isActive(),
                null,
                null,
                0,
                0
        );
    }

    public static TrainingVideoResponse forStaff(TrainingVideo video, UserTrainingVideoProgress progress) {
        if (progress == null) {
            return new TrainingVideoResponse(
                    video.getId(),
                    video.getTitle(),
                    video.getVideoUrl(),
                    video.getSortOrder(),
                    true,
                    TrainingStatus.NOT_STARTED,
                    BigDecimal.ZERO,
                    0,
                    0
            );
        }

        return new TrainingVideoResponse(
                video.getId(),
                video.getTitle(),
                video.getVideoUrl(),
                video.getSortOrder(),
                true,
                progress.getStatus(),
                progress.getProgressRate(),
                progress.getWatchedSeconds(),
                progress.getLastWatchedSeconds()
        );
    }
}
