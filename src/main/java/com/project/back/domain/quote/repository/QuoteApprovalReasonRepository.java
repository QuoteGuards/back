package com.project.back.domain.quote.repository;

import com.project.back.domain.quote.entity.QuoteApprovalReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteApprovalReasonRepository extends JpaRepository<QuoteApprovalReason, Long> {

    // 견적 승인 사유 목록 조회
    List<QuoteApprovalReason> findByQuoteId(Long quoteId);

    // 재계산 시 기존 사유 초기화 후 재저장
    void deleteByQuoteId(Long quoteId);
}
