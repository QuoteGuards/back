package com.project.back.domain.user.dto.response;

import com.project.back.domain.user.entity.UserStats;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class UserStatsResponse {

    private final Long userId;
    private final String userName;
    private final int totalQuotes;
    private final int approvedQuotes;
    private final int rejectedQuotes;
    private final int sentQuotes;
    private final BigDecimal totalAmount;
    private final BigDecimal totalSupplyAmount;
    private final BigDecimal totalProfitAmount;
    private final BigDecimal averageDiscountRate;
    private final BigDecimal averageProfitRate;
    private final LocalDateTime updatedAt;

    public static UserStatsResponse from(UserStats stats) {
        return UserStatsResponse.builder()
                .userId(stats.getUser().getId())
                .userName(stats.getUser().getName())
                .totalQuotes(stats.getTotalQuotes())
                .approvedQuotes(stats.getApprovedQuotes())
                .rejectedQuotes(stats.getRejectedQuotes())
                .sentQuotes(stats.getSentQuotes())
                .totalAmount(stats.getTotalAmount())
                .totalSupplyAmount(stats.getTotalSupplyAmount())
                .totalProfitAmount(stats.getTotalProfitAmount())
                .averageDiscountRate(stats.getAverageDiscountRate())
                .averageProfitRate(stats.getAverageProfitRate())
                .updatedAt(stats.getUpdatedAt())
                .build();
    }

    /**
     * 통계 데이터가 없는 경우 0값으로 채운 기본 응답 반환
     */
    public static UserStatsResponse empty(Long userId, String userName) {
        return UserStatsResponse.builder()
                .userId(userId)
                .userName(userName)
                .totalQuotes(0)
                .approvedQuotes(0)
                .rejectedQuotes(0)
                .sentQuotes(0)
                .totalAmount(BigDecimal.ZERO)
                .totalSupplyAmount(BigDecimal.ZERO)
                .totalProfitAmount(BigDecimal.ZERO)
                .averageDiscountRate(BigDecimal.ZERO)
                .averageProfitRate(BigDecimal.ZERO)
                .updatedAt(null)
                .build();
    }
}
