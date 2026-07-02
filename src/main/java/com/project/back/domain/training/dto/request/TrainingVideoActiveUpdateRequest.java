package com.project.back.domain.training.dto.request;

import jakarta.validation.constraints.NotNull;

public record TrainingVideoActiveUpdateRequest(
        @NotNull(message = "활성 여부는 필수입니다.")
        Boolean active
) {
}
