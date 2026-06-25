package com.project.back.ai.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConsultationSummaryRequest(

        @NotBlank(message = "상담 메모는 필수입니다.")
        String consultationMemo
) {
}
