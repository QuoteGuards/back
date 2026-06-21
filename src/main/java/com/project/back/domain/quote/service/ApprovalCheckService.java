package com.project.back.domain.quote.service;

import com.project.back.domain.discount.entity.DiscountPolicy;
import com.project.back.global.enums.ApprovalReasonType;
import com.project.back.domain.quote.entity.QuoteItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApprovalCheckService {

    public List<ApprovalReasonType> check(DiscountPolicy policy,
                                          List<QuoteItem> items,
                                          BigDecimal totalAmount,
                                          BigDecimal profitRate) {
        List<ApprovalReasonType> reasons = new ArrayList<>();

        if (policy == null) {
            return reasons;
        }

        if (isDiscountExceeded(policy.getMaxDiscountRate(), items)) {
            reasons.add(ApprovalReasonType.DISCOUNT_EXCEEDED);
        }

        if (isProfitRateLow(policy.getMinProfitRate(), profitRate)) {
            reasons.add(ApprovalReasonType.LOW_PROFIT);
        }

        if (isHighAmount(policy.getApprovalThresholdAmount(), totalAmount)) {
            reasons.add(ApprovalReasonType.HIGH_AMOUNT);
        }

        return reasons;
    }

    private boolean isDiscountExceeded(BigDecimal maxDiscountRate, List<QuoteItem> items) {
        if (maxDiscountRate == null) return false;
        return items.stream()
                .anyMatch(item -> item.getDiscountRate() != null
                        && item.getDiscountRate().compareTo(maxDiscountRate) > 0);
    }

    private boolean isProfitRateLow(BigDecimal minProfitRate, BigDecimal profitRate) {
        if (minProfitRate == null || profitRate == null) return false;
        return profitRate.compareTo(minProfitRate) < 0;
    }

    private boolean isHighAmount(BigDecimal threshold, BigDecimal totalAmount) {
        if (threshold == null || totalAmount == null) return false;
        return totalAmount.compareTo(threshold) > 0;
    }
}
