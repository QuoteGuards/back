package com.project.back.domain.quote.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.project.back.domain.quote.entity.QQuote.quote;
import static com.project.back.domain.customer.entity.QCustomer.customer;

@Repository
@RequiredArgsConstructor
public class QuoteRepositoryImpl implements QuoteRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Quote> searchMyQuotes(Long userId,
                                      QuoteStatus status,
                                      String customerName,
                                      String quoteNumber,
                                      LocalDateTime from,
                                      LocalDateTime to) {
        return queryFactory
                .selectFrom(quote)
                .join(quote.customer, customer).fetchJoin()
                .where(
                        eqUserId(userId),
                        eqStatus(status),
                        containsCustomerName(customerName),
                        containsQuoteNumber(quoteNumber),
                        betweenCreatedAt(from, to),
                        quote.isLatest.isTrue()
                )
                .orderBy(quote.createdAt.desc())
                .fetch();
    }

    private BooleanExpression eqUserId(Long userId) {
        return userId != null ? quote.createdBy.id.eq(userId) : null;
    }

    private BooleanExpression eqStatus(QuoteStatus status) {
        return status != null ? quote.status.eq(status) : null;
    }

    private BooleanExpression containsCustomerName(String customerName) {
        return (customerName != null && !customerName.isBlank())
                ? customer.companyName.containsIgnoreCase(customerName)
                : null;
    }

    private BooleanExpression containsQuoteNumber(String quoteNumber) {
        return (quoteNumber != null && !quoteNumber.isBlank())
                ? quote.quoteNumber.containsIgnoreCase(quoteNumber)
                : null;
    }

    private BooleanExpression betweenCreatedAt(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) return quote.createdAt.between(from, to);
        if (from != null) return quote.createdAt.goe(from);
        if (to != null) return quote.createdAt.loe(to);
        return null;
    }
}
