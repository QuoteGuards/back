package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.entity.UserTrainingProgress;
import com.project.back.domain.training.service.TrainingService.TrainingStatusResult;
import com.project.back.global.enums.TrainingStatus;

import java.math.BigDecimal;

public record TrainingStatusResponse(
        TrainingStatus status,
        BigDecimal progressRate,
        int watchedSeconds,
        int lastWatchedSeconds,
        boolean guideConfirmed,
        boolean completed
) {
    public static TrainingStatusResponse from(TrainingStatusResult result) {
        UserTrainingProgress progress = result.progress();

        if (progress == null) {
            return new TrainingStatusResponse(
                    TrainingStatus.NOT_STARTED,
                    BigDecimal.ZERO,
                    0,
                    0,
                    result.guideConfirmed(),
                    false
            );
        }

        return new TrainingStatusResponse(
                progress.getStatus(),
                progress.getProgressRate(),
                progress.getWatchedSeconds(),
                progress.getLastWatchedSeconds(),
                result.guideConfirmed(),
                result.isCompleted()
        );
    }
}
