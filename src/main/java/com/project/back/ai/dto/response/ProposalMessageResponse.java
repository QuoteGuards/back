package com.project.back.ai.dto.response;

import lombok.Builder;

@Builder
public record ProposalMessageResponse (
        String customerName,
        String customerCompany,
        String proposalMessage,
        boolean saved
){
}
