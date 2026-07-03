# 승인 요청 철회 API 설계

**날짜:** 2026-07-03
**담당:** 승인/반려 파트
**대상 브랜치:** feature/approval

---

## 개요

`ApprovalRequest.cancel()`은 이미 존재하지만, 지금은 `QuoteService.cancelQuote()`(견적 전체 취소)를 통해서만 간접적으로 호출된다. 잘못 올린 승인 요청 하나만 철회하고 싶어도, 견적 전체를 취소하는 것 말고는 방법이 없다. PENDING 상태의 승인 요청을 요청자 본인이 직접 철회할 수 있는 API를 추가한다.

**부수 효과:** 지금 `QuoteApprovalHistory.ActionType.CANCELLED`가 enum에는 정의돼 있지만 실제로 어디서도 이력이 기록되지 않는 버그가 있다. 이번에 `saveHistory(..., CANCELLED, ...)` 호출을 추가하면서 이 버그도 같이 해결한다 (견적 전체 취소 경로는 이번 스펙 범위 밖 — 필요하면 별도 스펙으로).

---

## 요구사항

- **권한:** 요청자 본인만. 담당 영업관리자도 대신 철회할 수 없다 (실수로 남의 요청을 철회하는 위험을 피하기 위해).
- **선행 조건:** 승인 요청이 `PENDING` 상태여야 한다 (`ApprovalRequest.cancel()`이 이미 검증).
- **견적 상태 전환:** `APPROVAL_PENDING → DRAFT`. 기존 `Quote.saveAsDraft()`를 재사용한다 (새 엔티티 메서드 불필요).
  - `DRAFT`를 선택한 이유: 견적 수정이 가능한 상태는 `DRAFT`/`REVISING` 둘뿐인데, `REVISING`은 "반려 후 수정중"이라는 의미라 자진 철회 상황과 맞지 않는다.
- **이력:** `CANCELLED` 액션으로 기록 (요청자 = actor).

---

## API

```
POST /api/quotes/{quoteId}/approval-requests/{approvalRequestId}/cancel
```
- 권한: `SALES_STAFF`, `SALES_MANAGER` (기존 `requestApproval`/`reRequest`와 동일)
- 요청 본문: 없음
- 응답: `ApprovalRequestResponse` (승인/반려/재요청 API와 동일한 응답 형태)

### 에러
| 상황 | ErrorCode |
|------|-----------|
| 경로의 quoteId와 실제 견적 불일치 | `APPROVAL_QUOTE_MISMATCH` |
| 요청자 본인이 아님 | `APPROVAL_ACCESS_DENIED` |
| PENDING 상태가 아님 (이미 처리/철회됨) | `APPROVAL_NOT_PENDING` |

---

## 전체 흐름

```
ApprovalService.cancelRequest(quoteId, approvalRequestId, requesterId)
        │
findApprovalRequestById + validateQuoteMatch
        │
요청자 본인 확인 (아니면 APPROVAL_ACCESS_DENIED)
        │
approvalRequest.cancel()  ← 기존 메서드, PENDING 아니면 예외
        │
quote.saveAsDraft()       ← 기존 메서드 재사용
        │
saveHistory(approvalRequest, requester, CANCELLED, PENDING, CANCELLED, null)
        │
ApprovalRequestResponse 반환
```

---

## 새로 만들 파일 / 수정할 파일

| 파일 | 변경 내용 |
|------|---------|
| `domain/approval/service/ApprovalService.java` | `cancelRequest(Long quoteId, Long approvalRequestId, Long requesterId)` 추가 |
| `domain/approval/controller/ApprovalController.java` | `POST /api/quotes/{quoteId}/approval-requests/{approvalRequestId}/cancel` 추가 |
| `quoteguard-front/src/api/approvalApi.js` | `cancelApprovalRequest(quoteId, approvalRequestId)` 추가 |
| `quoteguard-front/src/pages/approval/StaffApprovalPage.jsx` | PENDING 목록에 "철회" 버튼 + 확인 모달 추가 |

SQL/스키마 변경 없음 (기존 컬럼·enum만 재사용).

---

## 엣지 케이스

- **이미 승인/반려/철회된 요청을 다시 철회 시도:** `APPROVAL_NOT_PENDING` — 프론트는 목록을 새로고침해서 최신 상태를 반영
- **본인이 아닌 다른 영업사원의 요청을 철회 시도:** `APPROVAL_ACCESS_DENIED`
- **철회 후 재요청:** 견적이 `DRAFT`로 돌아갔으므로, 다시 승인 요청을 하려면 `QuoteService`의 제출 흐름(작성 → 제출 → 승인요청)을 처음부터 다시 거쳐야 한다 — `reRequest()`(반려 전용 재요청 API)는 사용할 수 없다. 이는 의도된 동작이다.

---

## 테스트 계획

`ApprovalServiceTest`에 `cancelRequest` 관련 `@Nested` 클래스 추가:
- 정상 철회 → `ApprovalRequest.status == CANCELLED`, `quote.saveAsDraft()` 호출 확인, `CANCELLED` 이력 저장 확인
- 본인이 아닌 요청자가 철회 시도 → `APPROVAL_ACCESS_DENIED`
- PENDING이 아닌 요청 철회 시도(예: 이미 승인됨) → `APPROVAL_NOT_PENDING`
- 경로 quoteId와 실제 견적 불일치 → `APPROVAL_QUOTE_MISMATCH`
