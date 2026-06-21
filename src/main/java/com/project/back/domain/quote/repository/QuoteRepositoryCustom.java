package com.project.back.domain.quote.repository;

import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;

import java.time.LocalDateTime;
import java.util.List;

public interface QuoteRepositoryCustom {

    // 내 견적 목록 다중 조건 검색 (상태 + 기간 + 고객명 + 견적번호 동시 필터)
    List<Quote> searchMyQuotes(Long userId,
                               QuoteStatus status,
                               String customerName,
                               String quoteNumber,
                               LocalDateTime from,
                               LocalDateTime to);
}
