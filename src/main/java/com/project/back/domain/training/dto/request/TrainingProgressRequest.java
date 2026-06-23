package com.project.back.domain.training.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TrainingProgressRequest(

        @NotNull(message = "진도율은 필수입니다.")
        @DecimalMin(value = "0.00", message = "진도율은 0 이상이어야 합니다.")
        @DecimalMax(value = "100.00", message = "진도율은 100 이하여야 합니다.")
        BigDecimal progressRate,

        @Min(value = 0, message = "시청 시간은 0 이상이어야 합니다.")
        int watchedSeconds,

        @Min(value = 0, message = "마지막 시청 위치는 0 이상이어야 합니다.")
        int lastWatchedSeconds
) {}
