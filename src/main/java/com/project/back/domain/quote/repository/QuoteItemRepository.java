package com.project.back.domain.quote.repository;

import com.project.back.domain.quote.entity.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteItemRepository extends JpaRepository<QuoteItem, Long> {

    // 견적 항목 목록 (정렬 순서 기준)
    List<QuoteItem> findByQuoteIdOrderBySortOrderAsc(Long quoteId);

    // 견적 수정 시 기존 항목 전체 삭제 후 재등록
    void deleteByQuoteId(Long quoteId);

    // 제품이 견적 항목에 사용 중인지 (제품 삭제 제한용)
    boolean existsByProductId(Long productId);
}
