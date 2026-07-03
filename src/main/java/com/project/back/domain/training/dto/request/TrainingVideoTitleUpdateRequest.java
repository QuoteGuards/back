package com.project.back.domain.training.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrainingVideoTitleUpdateRequest(
        @NotBlank(message = "영상 제목은 필수입니다.")
        @Size(max = 100, message = "영상 제목은 100자 이하여야 합니다.")
        String title
) {
}
