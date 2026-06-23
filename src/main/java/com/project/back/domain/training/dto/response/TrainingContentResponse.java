package com.project.back.domain.training.dto.response;

import com.project.back.domain.training.entity.TrainingContent;

public record TrainingContentResponse(
        Long id,
        String title,
        String description,
        String videoUrl,
        String guideContent,
        boolean required
) {
    public static TrainingContentResponse from(TrainingContent content) {
        return new TrainingContentResponse(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                content.getVideoUrl(),
                content.getGuideContent(),
                content.isRequired()
        );
    }
}
