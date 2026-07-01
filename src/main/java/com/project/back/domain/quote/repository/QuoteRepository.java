package com.project.back.domain.quote.repository;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.user.entity.User;
import com.project.back.global.enums.QuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface QuoteRepository extends JpaRepository<Quote, Long>, QuoteRepositoryCustom {

    List<Quote> findByCreatedByIdAndStatusAndIsLatestTrueOrderByCreatedAtDesc(
            Long userId, QuoteStatus status);

    List<Quote> findByCreatedByIdAndIsLatestTrueOrderByCreatedAtDesc(Long userId);

    List<Quote> findByCreatedByOrderByCreatedAtDesc(User createdBy);

    List<Quote> findByOriginalQuoteIdOrderByVersionNoAsc(Long originalQuoteId);

    Optional<Quote> findByQuoteNumber(String quoteNumber);

    Optional<Quote> findByQuoteNumberAndCreatedBy(String quoteNumber, User createdBy);

    boolean existsByQuoteNumber(String quoteNumber);

    @Query("SELECT DISTINCT q FROM Quote q " +
            "JOIN FETCH q.customer " +
            "LEFT JOIN FETCH q.discountPolicy " +
            "LEFT JOIN FETCH q.items " +
            "WHERE q.id = :id")
    Optional<Quote> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT q FROM Quote q " +
            "JOIN FETCH q.customer " +
            "LEFT JOIN FETCH q.discountPolicy " +
            "LEFT JOIN FETCH q.items " +
            "WHERE q.quoteNumber = :quoteNumber AND q.createdBy = :user")
    Optional<Quote> findByQuoteNumberAndCreatedByWithDetails(
            @Param("quoteNumber") String quoteNumber,
            @Param("user") User user);

    @Query("SELECT DISTINCT q FROM Quote q " +
            "LEFT JOIN FETCH q.approvalReasons " +
            "WHERE q.id = :id")
    Optional<Quote> findByIdWithApprovalReasons(@Param("id") Long id);

    @Query("SELECT q FROM Quote q " +
            "WHERE q.validUntil < CURRENT_DATE " +
            "AND q.status IN :statuses")
    List<Quote> findExpiredQuotes(@Param("statuses") List<QuoteStatus> statuses);

    // 특정 만료일(예: 3일 후)에 만료되는 최신 견적을 작성자와 함께 조회 (만료 임박 알림용)
    @Query("SELECT q FROM Quote q JOIN FETCH q.createdBy " +
            "WHERE q.validUntil = :date " +
            "AND q.status IN :statuses " +
            "AND q.isLatest = true")
    List<Quote> findExpiringOn(@Param("date") java.time.LocalDate date,
                               @Param("statuses") List<QuoteStatus> statuses);

    @Query("SELECT MAX(q.quoteNumber) FROM Quote q WHERE q.quoteNumber LIKE :prefix%")
    Optional<String> findMaxQuoteNumberByPrefix(@Param("prefix") String prefix);
}