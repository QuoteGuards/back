package com.project.back.domain.user.service;

import com.project.back.domain.quote.repository.QuoteRepository;
import com.project.back.domain.user.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 사용자 통계 배치 재집계 서비스.
 *
 * <h3>역할</h3>
 * <p>{@link UserStatsUpdateService}와 별도 빈으로 분리하여 Spring AOP 프록시를 통한
 * {@code REQUIRES_NEW} 트랜잭션이 정상 적용되도록 한다 (자기 호출 방지).</p>
 *
 * <h3>배치 대상</h3>
 * <p>다음 두 그룹의 합집합(Union)을 대상으로 한다.
 * <ol>
 *   <li>이미 {@code UserStats} 행이 존재하는 사용자 → 통계가 0이 되어야 하는 경우 초기화</li>
 *   <li>상태·버전 무관하게 한 건이라도 견적을 생성한 사용자 → 신규 집계</li>
 * </ol>
 *
 * <h3>실행 시각</h3>
 * <p>매일 02:00. 자정에 실행되는 만료 스케줄러({@code expireOverdueQuotes}) 이후에
 * 실행되므로 EXPIRED 상태 견적의 통계도 정상 반영된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatsBatchService {

    private final UserStatsUpdateService userStatsUpdateService;
    private final UserStatsRepository userStatsRepository;
    private final QuoteRepository quoteRepository;

    /**
     * 매일 02:00: 통계 갱신 대상 전 사용자를 재집계한다.
     * 한 사용자 처리 실패 시 로그를 남기고 다음 사용자를 계속 처리한다.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional(readOnly = true)
    public void recalculateAllUsers() {
        // UserStats 보유 사용자 + 견적 생성 사용자 합집합
        Set<Long> userIds = new HashSet<>(userStatsRepository.findAllUserIds());
        userIds.addAll(quoteRepository.findAllCreatedByUserIds());

        log.info("사용자 통계 배치 재집계 시작 [대상 사용자 수={}]", userIds.size());

        for (Long userId : userIds) {
            try {
                // REQUIRES_NEW: 각 사용자별 독립 트랜잭션
                userStatsUpdateService.recalculate(userId);
            } catch (Exception e) {
                // 개별 실패는 격리하여 다른 사용자 처리에 영향을 주지 않음
                log.error("사용자 통계 배치 갱신 실패 [userId={}]", userId, e);
            }
        }

        log.info("사용자 통계 배치 재집계 완료");
    }
}
