package com.project.back.domain.quote.repository;

import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long>, QuoteRepositoryCustom {

    // 내 견적 목록 (상태별 필터, 최신 버전만)
    List<Quote> findByCreatedByIdAndStatusAndIsLatestTrueOrderByCreatedAtDesc(
            Long userId, QuoteStatus status);

    // 내 전체 견적 (최신 버전만)
    List<Quote> findByCreatedByIdAndIsLatestTrueOrderByCreatedAtDesc(Long userId);

    // 견적 상세 조회
    @Query("SELECT DISTINCT q FROM Quote q " +
            "JOIN FETCH q.customer " +
            "LEFT JOIN FETCH q.items " +
            "LEFT JOIN FETCH q.approvalReasons " +
            "WHERE q.id = :id")
    Optional<Quote> findByIdWithDetails(@Param("id") Long id);

    // 원본 견적 기준 버전 이력 조회 (재사용/재작성 이력)
    List<Quote> findByOriginalQuoteIdOrderByVersionNoAsc(Long originalQuoteId);

    // 견적번호로 조회 (중복 방지 체크용)
    boolean existsByQuoteNumber(String quoteNumber);

    // 만료 처리 대상 조회 (배치 또는 스케줄러용)
    @Query("SELECT q FROM Quote q " +
            "WHERE q.validUntil < CURRENT_DATE " +
            "AND q.status IN :statuses")
    List<Quote> findExpiredQuotes(@Param("statuses") List<QuoteStatus> statuses);
}
