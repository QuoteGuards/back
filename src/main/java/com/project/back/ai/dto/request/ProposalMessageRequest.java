package com.project.back.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProposalMessageRequest (

        @NotBlank(message = "고객명은 필수입니다.")
        String customerName,

        @NotBlank(message = "고객사는 필수입니다.")
        String customerCompany,

        @NotBlank(message = "상담 메모는 필수입니다.")
        String consultationMemo,

        @NotEmpty(message = "제품 목록은 필수입니다.")
        List<@NotBlank(message = "제품명은 비어 있을 수 없습니다.") String> productNames
){
}
