package com.project.back.domain.user.service;

import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserStats;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.repository.UserStatsRepository;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 사용자 통계 데이터 수집 및 갱신 서비스.
 * <p>
 * 통계 갱신 전략: 견적 상태 변경 시점(제출·승인·반려)마다 해당 사용자의
 * 통계를 Quote 테이블 전체 재집계로 덮어쓴다. 이를 통해 상태 변경 순서와 무관하게
 * 항상 일관된 값을 보장한다.
 * <p>
 * 추가로 매일 새벽 2시에 전 사용자 대상 배치 재집계를 실행하여
 * 혹시라도 누락된 갱신을 보정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatsUpdateService {

    /**
     * 통계 집계에서 제외할 견적 상태
     */
    private static final List<QuoteStatus> EXCLUDED_STATUSES =
            List.of(QuoteStatus.DRAFT, QuoteStatus.CANCELLED);

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final QuoteRepository quoteRepository;

    /**
     * 특정 사용자의 통계를 Quote 테이블 기준으로 전체 재집계하여 갱신한다.
     *
     * @param userId 통계를 갱신할 사용자 ID
     */
    @Transactional
    public void recalculate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Quote> quotes = quoteRepository.findSubmittedByUserId(userId, EXCLUDED_STATUSES);

        int totalQuotes = quotes.size();
        int approvedQuotes = (int) quotes.stream().filter(q -> isApproved(q.getStatus())).count();
        int rejectedQuotes = (int) quotes.stream().filter(q -> q.getStatus() == QuoteStatus.REJECTED).count();
        int sentQuotes = (int) quotes.stream().filter(q -> q.getStatus() == QuoteStatus.SENT).count();

        // 승인·발송 상태 견적의 금액 합계
        List<Quote> closedQuotes = quotes.stream().filter(q -> isApproved(q.getStatus())).toList();
        BigDecimal totalAmount = sum(closedQuotes, Quote::getTotalAmount);
        BigDecimal totalSupplyAmount = sum(closedQuotes, Quote::getSupplyAmount);
        BigDecimal totalProfitAmount = sum(closedQuotes, Quote::getExpectedProfitAmount);

        // 전체 제출 견적 기준 평균 할인율·이익률
        BigDecimal avgDiscountRate = avgDiscountRate(quotes);
        BigDecimal avgProfitRate = avg(quotes, Quote::getProfitRate);

        UserStats stats = userStatsRepository.findByUserId(userId)
                .orElseGet(() -> UserStats.builder().user(user).build());

        stats.update(totalQuotes, approvedQuotes, rejectedQuotes, sentQuotes,
                totalAmount, totalSupplyAmount, totalProfitAmount,
                avgDiscountRate, avgProfitRate);

        userStatsRepository.save(stats);
        log.debug("사용자 통계 갱신 완료 [userId={}, totalQuotes={}]", userId, totalQuotes);
    }

    /**
     * 매일 새벽 2시: 견적 데이터가 있는 전 사용자 통계를 배치 재집계한다.
     * 이벤트 훅에서 누락된 갱신을 보정하는 안전망 역할을 한다.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void recalculateAllUsers() {
        List<Long> userIds = quoteRepository.findUserIdsWithSubmittedQuotes(EXCLUDED_STATUSES);
        log.info("사용자 통계 배치 재집계 시작 [대상 사용자 수={}]", userIds.size());
        userIds.forEach(userId -> {
            try {
                recalculate(userId);
            } catch (Exception e) {
                log.error("사용자 통계 배치 갱신 실패 [userId={}]", userId, e);
            }
        });
        log.info("사용자 통계 배치 재집계 완료");
    }

    // ── 집계 헬퍼 ────────────────────────────────────────────────────────

    private boolean isApproved(QuoteStatus status) {
        return status == QuoteStatus.APPROVED || status == QuoteStatus.SENT;
    }

    @FunctionalInterface
    private interface QuoteField {
        BigDecimal get(Quote q);
    }

    private BigDecimal sum(List<Quote> quotes, QuoteField field) {
        return quotes.stream()
                .map(field::get)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal avg(List<Quote> quotes, QuoteField field) {
        if (quotes.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = sum(quotes, field);
        return total.divide(BigDecimal.valueOf(quotes.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 할인율 = (할인금액 / 소계) * 100 평균.
     * 소계가 0인 견적은 제외한다.
     */
    private BigDecimal avgDiscountRate(List<Quote> quotes) {
        List<Quote> withSubtotal = quotes.stream()
                .filter(q -> q.getSubtotal() != null && q.getSubtotal().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (withSubtotal.isEmpty()) return BigDecimal.ZERO;

        BigDecimal sumRate = withSubtotal.stream()
                .map(q -> q.getDiscountAmount()
                        .divide(q.getSubtotal(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sumRate.divide(BigDecimal.valueOf(withSubtotal.size()), 2, RoundingMode.HALF_UP);
    }
}
