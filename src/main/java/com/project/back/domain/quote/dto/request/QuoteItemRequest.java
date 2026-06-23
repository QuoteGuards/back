package com.project.back.domain.quote.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record QuoteItemRequest(

        Long productId,

        @NotBlank(message = "제품명은 필수입니다.")
        @Size(max = 255)
        String productName,

        @Size(max = 100)
        String productCode,

        @NotBlank(message = "규격(spec)은 필수입니다.")
        @Size(max = 200)
        String spec,           // 추가

        @NotNull(message = "단가는 필수입니다.")
        @DecimalMin(value = "0", message = "단가는 0 이상이어야 합니다.")
        BigDecimal unitPrice,

        @NotNull(message = "원가는 필수입니다.")
        @DecimalMin(value = "0", message = "원가는 0 이상이어야 합니다.")
        BigDecimal costPrice,

        @NotNull(message = "수량은 필수입니다.")
        @DecimalMin(value = "0.01", message = "수량은 0보다 커야 합니다.")
        BigDecimal quantity,

        @DecimalMin(value = "0", message = "할인율은 0 이상이어야 합니다.")
        @DecimalMax(value = "100", message = "할인율은 100 이하이어야 합니다.")
        BigDecimal discountRate,

        @NotNull(message = "VAT 적용 여부는 필수입니다.")
        Boolean vatApplicable
) {}
