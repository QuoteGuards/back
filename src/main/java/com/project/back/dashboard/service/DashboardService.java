package com.project.back.dashboard.service;


import com.project.back.dashboard.dto.DashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final QuoteRepository quoteRepository;

    public DashboardSummaryResponse getSummary(Long userId) {

        UserStatsProjection stats =
                quoteRepository.aggregateUserStats(userId);

        if (stats == null) {
            stats = UserStatsProjection.empty();
        }

        return new DashboardSummaryResponse(
                stats.totalQuotes(),
                stats.approvedQuotes(),
                stats.rejectedQuotes(),
                stats.sentQuotes(),

                stats.safeAmount(stats.totalAmount()),
                stats.safeAmount(stats.totalSupplyAmount()),
                stats.safeAmount(stats.totalProfitAmount()),

                stats.calcAverageDiscountRate(),
                stats.calcAverageProfitRate()
        );
    }
}
