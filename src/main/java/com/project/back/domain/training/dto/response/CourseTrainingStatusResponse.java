package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.service.TrainingService.CourseTrainingStatusResult;
import com.project.back.global.enums.TrainingStatus;
import com.project.back.global.enums.TrainingType;

import java.math.BigDecimal;
import java.util.List;

public record CourseTrainingStatusResponse(
        TrainingType trainingType,
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
    public static CourseTrainingStatusResponse from(CourseTrainingStatusResult result) {
        return new CourseTrainingStatusResponse(
                result.trainingType(),
                result.aggregateStatus(),
                result.aggregateProgressRate(),
                result.aggregateWatchedSeconds(),
                result.aggregateLastWatchedSeconds(),
                result.guideConfirmed(),
                result.completed(),
                result.activeVideoCount(),
                result.completedVideoCount(),
                result.additionalTrainingRequired(),
                result.videos()
        );
    }
}
