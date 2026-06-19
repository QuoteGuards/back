package com.project.back.domain.quote.repository;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findByCreatedByOrderByCreatedAtDesc(User createdBy);

    Optional<Quote> findByQuoteNumber(String quoteNumber);

    Optional<Quote> findByQuoteNumberAndCreatedBy(String quoteNumber, User createdBy);

    boolean existsByQuoteNumber(String quoteNumber);

    @Query("SELECT MAX(q.quoteNumber) FROM Quote q WHERE q.quoteNumber LIKE :prefix%")
    Optional<String> findMaxQuoteNumberByPrefix(@Param("prefix") String prefix);
}
