package com.project.back.domain.training.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TrainingProgressRequest(

        @NotNull(message = "진도율은 필수입니다.")
        BigDecimal progressRate,

        @NotNull(message = "시청 시간은 필수입니다.")
        @Min(value = 0, message = "시청 시간은 0 이상이어야 합니다.")
        Integer watchedSeconds,

        @NotNull(message = "마지막 시청 위치는 필수입니다.")
        @Min(value = 0, message = "마지막 시청 위치는 0 이상이어야 합니다.")
        Integer lastWatchedSeconds
) {}
