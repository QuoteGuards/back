package com.project.back.domain.quote.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record QuoteUpdateRequest(

        @NotNull(message = "고객 정보는 필수입니다.")
        Long customerId,

        String internalMemo,

        @NotNull(message = "발행일은 필수입니다.")
        LocalDate issuedDate,    // 추가

        LocalDate validUntil,

        @NotBlank(message = "납기 조건은 필수입니다.")
        String deliveryTerm,     // 추가

        @NotEmpty(message = "견적 항목은 최소 1개 이상이어야 합니다.")
        @Valid
        List<QuoteItemRequest> items
) {}
