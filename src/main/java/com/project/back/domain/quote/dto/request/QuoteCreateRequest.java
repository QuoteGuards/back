package com.project.back.domain.quote.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record QuoteCreateRequest(

        @NotNull(message = "고객 정보는 필수입니다.")
        Long customerId,

        /** 하위 호환용. 서버는 품목별 resolveApplicablePolicy로 policy를 결정하며 이 값은 사용하지 않음. */
        Long discountPolicyId,

        @NotNull(message = "발행일은 필수입니다.")
        LocalDate issuedDate,    // 추가

        String internalMemo,

        @NotNull(message = "견적 유효기간(만료일)은 필수입니다.")
        LocalDate validUntil,

        @NotBlank(message = "납기 조건은 필수입니다.")
        String deliveryTerm,     // 추가

        @NotEmpty(message = "견적 항목은 최소 1개 이상이어야 합니다.")
        @Valid
        List<QuoteItemRequest> items
) {}
