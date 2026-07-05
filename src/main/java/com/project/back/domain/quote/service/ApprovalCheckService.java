package com.project.back.domain.quote.service;

import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.domain.quote.entity.QuoteItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ApprovalCheckService {

    /**
     * 품목별 policy 스냅샷 기준 승인 필요 여부 판단.
     * - DISCOUNT_EXCEEDED: 각 품목 할인율 vs 해당 품목 policy.maxDiscountRate
     * - LOW_PROFIT: 견적 전체 profitRate vs 품목 policy 중 max(minProfitRate)
     * - HIGH_AMOUNT: 견적 totalAmount vs 품목 policy 중 min(approvalThresholdAmount)
     */
    public List<ApprovalReasonType> check(List<QuoteItem> items,
                                          BigDecimal totalAmount,
                                          BigDecimal profitRate) {
        List<ApprovalReasonType> reasons = new ArrayList<>();

        if (items == null || items.isEmpty()) {
            return reasons;
        }

        if (isAnyItemDiscountExceeded(items)) {
            reasons.add(ApprovalReasonType.DISCOUNT_EXCEEDED);
        }

        BigDecimal strictestMinProfit = resolveStrictestMinProfitRate(items);
        if (isProfitRateLow(strictestMinProfit, profitRate)) {
            reasons.add(ApprovalReasonType.LOW_PROFIT);
        }

        BigDecimal strictestThreshold = resolveStrictestApprovalThreshold(items);
        if (isHighAmount(strictestThreshold, totalAmount)) {
            reasons.add(ApprovalReasonType.HIGH_AMOUNT);
        }

        return reasons;
    }

    private boolean isAnyItemDiscountExceeded(List<QuoteItem> items) {
        return items.stream().anyMatch(this::isItemDiscountExceeded);
    }

    private boolean isItemDiscountExceeded(QuoteItem item) {
        BigDecimal maxDiscountRate = item.getEffectiveMaxDiscountRate();
        if (maxDiscountRate == null) {
            return false;
        }
        BigDecimal rate = item.getDiscountRate();
        if (rate == null) {
            return false;
        }
        return rate.compareTo(maxDiscountRate) > 0;
    }

    private BigDecimal resolveStrictestMinProfitRate(List<QuoteItem> items) {
        return items.stream()
                .map(QuoteItem::getEffectiveMinProfitRate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private BigDecimal resolveStrictestApprovalThreshold(List<QuoteItem> items) {
        return items.stream()
                .map(QuoteItem::getEffectiveApprovalThresholdAmount)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean isProfitRateLow(BigDecimal minProfitRate, BigDecimal profitRate) {
        if (minProfitRate == null || profitRate == null) {
            return false;
        }
        return profitRate.compareTo(minProfitRate) < 0;
    }

    private boolean isHighAmount(BigDecimal threshold, BigDecimal totalAmount) {
        if (threshold == null || totalAmount == null) {
            return false;
        }
        return totalAmount.compareTo(threshold) >= 0;
    }
}
