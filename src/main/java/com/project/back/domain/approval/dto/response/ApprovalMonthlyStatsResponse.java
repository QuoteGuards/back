package com.project.back.domain.approval.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApprovalMonthlyStatsResponse {
    private long monthlyApproved;
    private long monthlyRejected;
}
