package com.project.back.domain.user.service;

import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.dto.UserStatsProjection;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.entity.UserStats;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.domain.user.repository.UserStatsRepository;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 통계 데이터 수집 및 갱신 서비스.
 *
 * <h3>갱신 전략: DB 전체 재집계</h3>
 * <p>견적 상태 변경 시점마다 해당 사용자의 {@code UserStats}를 Quote 테이블 기준으로
 * 전체 재집계하여 덮어쓴다. 이를 통해 상태 변경 순서·버전에 무관하게 항상 정합성을 보장한다.</p>
 *
 * <h3>트랜잭션 전략: REQUIRES_NEW</h3>
 * <p>배치 서비스({@link UserStatsBatchService})나 상태 변경 서비스에서 호출될 때
 * 각 사용자별로 독립적인 트랜잭션을 보장한다. 한 사용자 갱신 실패가 다른 사용자나
 * 호출자 트랜잭션에 영향을 주지 않는다.</p>
 *
 * <h3>동시성: 낙관적 락</h3>
 * <p>{@code UserStats}에 {@code @Version}을 적용하여 동시 갱신 시 나중에 커밋된
 * 트랜잭션에서 {@code OptimisticLockException}을 발생시킨다. 배치의 경우 충돌 자체가
 * 거의 없고, 이벤트 훅의 경우 호출 빈도가 낮아 낙관적 락이 적절한 수준의 동시성 처리다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatsUpdateService {

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final QuoteRepository quoteRepository;

    /**
     * 특정 사용자의 통계를 DB 집계 쿼리로 재계산하여 {@code UserStats}에 반영한다.
     *
     * <p>집계 기준:
     * <ul>
     *   <li>전체 견적 수: isLatest=true, DRAFT·CANCELLED 제외</li>
     *   <li>승인 완료: APPROVED·SENT·EXPIRED</li>
     *   <li>발송 완료: SENT·EXPIRED</li>
     *   <li>반려: REJECTED (최신 버전 기준)</li>
     *   <li>금액: APPROVED·SENT·EXPIRED 의 최신 버전 합계</li>
     * </ul>
     *
     * @param userId 통계를 갱신할 사용자 ID
     * @throws CustomException {@code USER_NOT_FOUND} 사용자가 존재하지 않을 때
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserStatsProjection proj = quoteRepository.aggregateUserStats(userId);

        UserStats stats = userStatsRepository.findByUserId(userId)
                .orElseGet(() -> UserStats.builder().user(user).build());

        stats.update(
                (int) proj.totalQuotes(),
                (int) proj.approvedQuotes(),
                (int) proj.rejectedQuotes(),
                (int) proj.sentQuotes(),
                proj.safeAmount(proj.totalAmount()),
                proj.safeAmount(proj.totalSupplyAmount()),
                proj.safeAmount(proj.totalProfitAmount()),
                proj.calcAverageDiscountRate(),
                proj.calcAverageProfitRate()
        );

        userStatsRepository.save(stats);
        log.debug("사용자 통계 갱신 완료 [userId={}, totalQuotes={}]", userId, proj.totalQuotes());
    }
}
