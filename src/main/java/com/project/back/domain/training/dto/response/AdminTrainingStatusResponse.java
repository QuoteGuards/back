package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.domain.training.entity.UserTrainingProgress;
import com.project.back.domain.user.entity.User;
import com.project.back.global.enums.TrainingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminTrainingStatusResponse(
        Long userId,
        String memberNumber,
        String userName,
        String email,
        String department,
        String trainingTitle,
        TrainingStatus status,
        BigDecimal progressRate,
        int watchedSeconds,
        int lastWatchedSeconds,
        boolean guideConfirmed,
        boolean fullyCompleted,
        LocalDateTime completedAt
) {
    public static AdminTrainingStatusResponse from(UserTrainingProgress progress) {
        return new AdminTrainingStatusResponse(
                progress.getUser().getId(),
                progress.getUser().getMemberNumber(),
                progress.getUser().getName(),
                progress.getUser().getEmail(),
                progress.getUser().getDepartment(),
                progress.getTrainingContent().getTitle(),
                progress.getStatus(),
                progress.getProgressRate(),
                progress.getWatchedSeconds(),
                progress.getLastWatchedSeconds(),
                false,
                progress.getStatus() == TrainingStatus.COMPLETED,
                progress.getCompletedAt()
        );
    }

    public static AdminTrainingStatusResponse from(
            User user,
            TrainingContent content,
            UserTrainingProgress progress,
            boolean guideConfirmed
    ) {
        if (progress == null) {
            return new AdminTrainingStatusResponse(
                    user.getId(),
                    user.getMemberNumber(),
                    user.getName(),
                    user.getEmail(),
                    user.getDepartment(),
                    content.getTitle(),
                    TrainingStatus.NOT_STARTED,
                    BigDecimal.ZERO,
                    0,
                    0,
                    guideConfirmed,
                    false,
                    null
            );
        }

        boolean fullyCompleted = progress.getStatus() == TrainingStatus.COMPLETED && guideConfirmed;

        return new AdminTrainingStatusResponse(
                user.getId(),
                user.getMemberNumber(),
                user.getName(),
                user.getEmail(),
                user.getDepartment(),
                content.getTitle(),
                progress.getStatus(),
                progress.getProgressRate(),
                progress.getWatchedSeconds(),
                progress.getLastWatchedSeconds(),
                guideConfirmed,
                fullyCompleted,
                progress.getCompletedAt()
        );
    }
}
