package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.entity.UserTrainingProgress;
import com.project.back.global.enums.TrainingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminTrainingStatusResponse(
        Long userId,
        String userName,
        String email,
        TrainingStatus status,
        BigDecimal progressRate,
        int watchedSeconds,
        LocalDateTime completedAt
) {
    public static AdminTrainingStatusResponse from(UserTrainingProgress progress) {
        return new AdminTrainingStatusResponse(
                progress.getUser().getId(),
                progress.getUser().getName(),
                progress.getUser().getEmail(),
                progress.getStatus(),
                progress.getProgressRate(),
                progress.getWatchedSeconds(),
                progress.getCompletedAt()
        );
    }
}
