package com.project.back.ai.dto.response;

import lombok.Builder;

@Builder
public record ConsultationSummaryResponse (
        String originalMemo,
        String summary,
        boolean saved
){
}
