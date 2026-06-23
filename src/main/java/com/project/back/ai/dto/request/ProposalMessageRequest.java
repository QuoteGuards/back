package com.project.back.ai.dto.request;

import java.util.List;

public record ProposalMessageRequest (
        String customerName,
        String customerCompany,
        String consultationMemo,
        List<String> productNames
){
}
