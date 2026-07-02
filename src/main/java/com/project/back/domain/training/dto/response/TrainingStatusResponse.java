package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.service.TrainingService.TrainingStatusResult;
import com.project.back.global.enums.TrainingStatus;

import java.math.BigDecimal;
import java.util.List;

public record TrainingStatusResponse(
        TrainingStatus status,
        BigDecimal progressRate,
        int watchedSeconds,
        int lastWatchedSeconds,
        boolean guideConfirmed,
        boolean completed,
        int activeVideoCount,
        int completedVideoCount,
        boolean additionalTrainingRequired,
        List<TrainingVideoResponse> videos
) {
    public static TrainingStatusResponse from(TrainingStatusResult result) {
        return new TrainingStatusResponse(
                result.aggregateStatus(),
                result.aggregateProgressRate(),
                result.aggregateWatchedSeconds(),
                result.aggregateLastWatchedSeconds(),
                result.guideConfirmed(),
                result.isCompleted(),
                result.activeVideoCount(),
                result.completedVideoCount(),
                result.additionalTrainingRequired(),
                result.videos()
        );
    }
}
