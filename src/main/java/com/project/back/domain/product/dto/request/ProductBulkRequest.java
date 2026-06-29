package com.project.back.domain.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

// 제품 일괄 처리(활성화/비활성화/삭제) 요청 - 제품 id 목록
@Getter
public class ProductBulkRequest {

    // 리스트 전체 비어있음(@NotEmpty) + 각 원소 null(@NotNull) 모두 차단
    @NotEmpty
    private List<@NotNull Long> ids;
}
