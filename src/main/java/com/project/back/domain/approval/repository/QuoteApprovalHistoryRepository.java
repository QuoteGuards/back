package com.project.back.domain.approval.repository;

import com.project.back.domain.approval.entity.QuoteApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuoteApprovalHistoryRepository extends JpaRepository<QuoteApprovalHistory, Long> {

    // 특정 승인 요청의 이력 목록 조회 (시간순)
    List<QuoteApprovalHistory> findByApprovalRequestIdOrderByActedAtAsc(
            Long approvalRequestId
    );

    // 특정 견적의 전체 승인 이력 조회
    @Query("""

            SELECT h FROM QuoteApprovalHistory h
                JOIN h.approvalRequest ar
                JOIN FETCH h.actor
                WHERE ar.quote.id = :quoteId
                ORDER BY h.actedAt ASC
        """)
    List<QuoteApprovalHistory> findAllByQuoteId(@Param("quoteId") Long quoteId);

    // 특정 승인 요청의 가장 최근 이력 조회
    @Query("""
            SELECT h FROM QuoteApprovalHistory h
            WHERE h.approvalRequest.id = :approvalRequestId
            ORDER BY h.actedAt DESC
            LIMIT 1
            """)
    Optional<QuoteApprovalHistory> findLatestByApprovalRequestId(
            @Param("approvalRequestId") Long approvalRequestId
    );
}