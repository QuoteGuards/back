package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.entity.TrainingContent;
import com.project.back.global.enums.TrainingType;

import java.util.List;

public record TrainingContentResponse(
        Long id,
        TrainingType trainingType,
        String title,
        String description,
        String videoUrl,
        String guideContent,
        boolean required,
        List<TrainingVideoResponse> videos
) {
    public static TrainingContentResponse from(TrainingContent content, List<TrainingVideoResponse> videos) {
        String legacyVideoUrl = videos.stream()
                .filter(TrainingVideoResponse::active)
                .map(TrainingVideoResponse::videoUrl)
                .findFirst()
                .orElse(content.getVideoUrl());

        return new TrainingContentResponse(
                content.getId(),
                content.getTrainingType(),
                content.getTitle(),
                content.getDescription(),
                legacyVideoUrl,
                content.getGuideContent(),
                content.isRequired(),
                videos
        );
    }
}
