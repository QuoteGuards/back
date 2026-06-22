package com.project.back.domain.approval.repository;

import com.project.back.domain.approval.entity.QuoteApprovalReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteApprovalReasonRepository extends JpaRepository<QuoteApprovalReason, Long> {

    // 특정 견적의 승인 필요 사유 목록 조회
    List<QuoteApprovalReason> findByQuote_Id(Long quoteId);

    // 특정 견적의 특정 사유 존재 여부 확인
    boolean existsByQuote_IdAndReasonType(
            Long quoteId,
            QuoteApprovalReason.ReasonType reasonType
    );

    // 특정 견적의 승인 필요 사유 전체 삭제 (재요청 시 사유 초기화)
    void deleteByQuote_Id(Long quoteId);
}