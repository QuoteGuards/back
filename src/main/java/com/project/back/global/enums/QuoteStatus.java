package com.project.back.global.enums;

public enum QuoteStatus {

    DRAFT,                  // 임시저장
    SUBMITTED,              // 작성완료
    APPROVAL_NOT_REQUIRED,  // 승인불필요 → 바로 발송 가능
    APPROVAL_PENDING,       // 승인대기
    APPROVED,               // 승인완료
    REJECTED,               // 반려
    REVISING,               // 수정중 (반려 후)
    SENT,                   // 발송완료
    EXPIRED,                // 만료
    CANCELLED               // 취소
}
