package com.project.back.domain.training.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TrainingGuideContentUpdateRequest(
        @NotBlank(message = "가이드 내용은 필수입니다.")
        String guideContent
) {
}
