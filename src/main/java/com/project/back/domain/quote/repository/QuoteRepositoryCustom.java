package com.project.back.domain.quote.repository;

import com.project.back.domain.user.dto.UserStatsProjection;
import com.project.back.global.enums.QuoteStatus;
import com.project.back.domain.quote.entity.Quote;

import java.time.LocalDateTime;
import java.util.List;

public interface QuoteRepositoryCustom {

    // 내 견적 목록 다중 조건 검색 (상태 + 기간 + 고객명 + 견적번호 동시 필터)
    List<Quote> searchMyQuotes(Long userId,
                               QuoteStatus status,
                               String customerName,
                               String quoteNumber,
                               LocalDateTime from,
                               LocalDateTime to);

    // 전체 견적 목록(SUPER_ADMIN 전용)
    List<Quote> searchAdminQuotes(QuoteStatus status,
                                  String customerName,
                                  String quoteNumber,
                                  String writerName,
                                  LocalDateTime from,
                                  LocalDateTime to);

    // 담당 영업사원 견적 SALES_MANAGER, 동일 department의 SALES_STAFF 견적
    List<Quote> searchManagerQuotes(String managerDepartment,
                                    QuoteStatus status,
                                    String customerName,
                                    String quoteNumber,
                                    String writerName,
                                    LocalDateTime from,
                                    LocalDateTime to);

    /**
     * 특정 사용자의 견적 통계를 DB에서 한 번의 집계 쿼리로 조회한다.
     * isLatest=true 이고 DRAFT·CANCELLED 제외한 최신 버전 견적만 대상으로 한다.
     */
    UserStatsProjection aggregateUserStats(Long userId);

    /**
     * 배치 재집계 대상 사용자 ID 전체 조회.
     * 상태 무관하게 한 건이라도 견적을 생성한 사용자를 모두 포함한다.
     */
    List<Long> findAllCreatedByUserIds();
}
