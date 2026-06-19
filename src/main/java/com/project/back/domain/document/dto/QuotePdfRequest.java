package com.project.back.domain.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record QuotePdfRequest(

        @NotBlank String quoteNumber,
        @NotNull  LocalDate issuedDate,
        @NotNull  LocalDate validUntil,
        @NotBlank String deliveryTerm,

        @NotNull @Valid CustomerRequest customer,
        @NotNull @Valid CompanyRequest  company,

        @NotEmpty @Valid List<QuoteItemRequest> items,

        @NotNull @DecimalMin("0") BigDecimal subtotal,
        @NotNull @DecimalMin("0") BigDecimal discountAmount,
        @NotNull @DecimalMin("0") BigDecimal taxAmount,
        @NotNull @DecimalMin("0") BigDecimal totalAmount,

        String internalMemo

) {
    public record CustomerRequest(
            @NotBlank String companyName,
            @NotBlank String contactName,
            String email,
            String phone,
            String address
    ) {}

    public record CompanyRequest(
            @NotBlank String name,
            String address,
            String phone,
            String email,
            String businessNumber
    ) {}

    public record QuoteItemRequest(
            int sortOrder,
            @NotBlank String productName,
            String spec,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice,
            @NotNull @DecimalMin("0") BigDecimal discountRate,
            @NotNull @DecimalMin("0") BigDecimal lineTotal
    ) {}
}
