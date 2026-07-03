package com.project.back.global.enums;

public enum ApprovalReasonType {

    DISCOUNT_EXCEEDED,  // 할인율이 정책 기준 초과
    LOW_PROFIT,         // 이익률이 최소 기준 미달
    HIGH_AMOUNT;        // 견적 총액이 고액 기준 이상

    public String getDefaultMessage() {
        return switch (this) {
            case DISCOUNT_EXCEEDED -> "할인율이 정책 기준을 초과했습니다.";
            case LOW_PROFIT -> "이익률이 최소 기준에 미달합니다.";
            case HIGH_AMOUNT -> "견적 총액이 고액 기준 이상입니다.";
        };
    }
}
