package com.project.back.domain.approval.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AiRiskSummaryResponse {

    private final Long approvalRequestId;
    private final String aiRiskSummary;
    private final boolean cached;

    public static AiRiskSummaryResponse cached(Long approvalRequestId, String summary) {
        return new AiRiskSummaryResponse(approvalRequestId, summary, true);
    }

    public static AiRiskSummaryResponse generated(Long approvalRequestId, String summary) {
        return new AiRiskSummaryResponse(approvalRequestId, summary, false);
    }
}
