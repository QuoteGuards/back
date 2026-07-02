package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.domain.training.service.TrainingService.CourseTrainingStatusResult;
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
        int activeVideoCount,
        int completedVideoCount,
        LocalDateTime completedAt
) {
    public static AdminTrainingStatusResponse from(
            User user,
            TrainingContent content,
            CourseTrainingStatusResult result
    ) {
        return new AdminTrainingStatusResponse(
                user.getId(),
                user.getMemberNumber(),
                user.getName(),
                user.getEmail(),
                user.getDepartment(),
                content.getTitle(),
                result.aggregateStatus(),
                result.aggregateProgressRate(),
                result.aggregateWatchedSeconds(),
                result.aggregateLastWatchedSeconds(),
                result.guideConfirmed(),
                result.completed(),
                result.activeVideoCount(),
                result.completedVideoCount(),
                result.completedAt()
        );
    }
}
