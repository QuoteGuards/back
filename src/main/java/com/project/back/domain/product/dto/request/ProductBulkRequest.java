package com.project.back.domain.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

// 제품 일괄 처리(활성화/비활성화/삭제) 요청 - 제품 id 목록
@Getter
public class ProductBulkRequest {

    @NotEmpty
    private List<Long> ids;
}
