package com.project.back.global.enums;

public enum ApprovalReasonType {

    DISCOUNT_EXCEEDED,  // 할인율이 정책 기준 초과
    LOW_PROFIT,         // 이익률이 최소 기준 미달
    HIGH_AMOUNT         // 견적 총액이 고액 기준 이상
}
