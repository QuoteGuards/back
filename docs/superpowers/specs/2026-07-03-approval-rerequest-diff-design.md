# 재요청 변경 내역(Diff) 표시 설계

**날짜:** 2026-07-03
**담당:** 승인/반려 파트
**대상 브랜치:** feature/approval

---

## 개요

반려된 견적을 영업사원이 수정해서 재요청했을 때, 검토자(SUPER_ADMIN/SALES_MANAGER)가 "반려 시점 대비 무엇이 바뀌었는지"를 승인 상세 화면에서 바로 볼 수 있게 한다.

**배경:** 지금은 `requestCount`로 몇 번째 재요청인지만 알 수 있고, 실제 변경 내용은 검토자가 견적 전체를 다시 읽어야 파악할 수 있다. 이게 재요청 심사 시간이 길어지고 같은 사유로 반복 반려되는 원인 중 하나다.

**이번 스펙의 범위:** 총액/이익률/할인율 증감 + 품목 추가·삭제·수량 변경만 보여준다. AI 요약에 "개선/악화" 코멘트를 붙이는 건 이번 범위에서 제외 (diff 데이터가 쌓인 뒤 별도 스펙으로 진행).

---

## 요구사항

- **조회 권한:** SUPER_ADMIN, SALES_MANAGER만. 요청자 본인(SALES_STAFF)에게는 노출하지 않는다.
- **비교 대상:** 가장 최근의 REJECTED 이력 ↔ 그 다음에 발생한 RE_REQUESTED 이력. 여러 번 반려·재요청이 반복돼도 가장 최근 한 쌍만 비교한다.
- **비교 불가 시:** 반려 이력이 아예 없으면(최초 요청) diff를 `null`로 반환하고, 화면에서는 해당 섹션을 숨긴다.
- **품목 식별 키:** `productId` — 견적 수정 시 품목을 항상 `productId` 기준으로 교체하는 기존 로직(`QuoteService.updateQuote`)과 동일한 방식.

---

## 데이터 모델

`quote_approval_histories` 테이블에 컬럼 추가:

```sql
quote_snapshot TEXT NULL COMMENT '반려/재요청 시점의 견적 스냅샷(JSON) - 재요청 시 변경 항목(총액/이익률/할인율/품목 증감) 비교용',
```

- `action`이 `REJECTED` 또는 `RE_REQUESTED`일 때만 값이 채워진다. 그 외(`REQUESTED`/`APPROVED`/`CANCELLED`)는 계속 `NULL`.
- JPA 엔티티(`QuoteApprovalHistory`)에 `@Column(columnDefinition = "TEXT") private String quoteSnapshot;` 추가. `ddl-auto=update`로 로컬 DB는 자동 반영되며, `sql/QuoteGuard.sql`(및 루트 사본)의 `CREATE TABLE` 문도 함께 동기화한다.

**스냅샷 JSON 구조 (Jackson으로 직렬화):**
```json
{
  "totalAmount": 12000000,
  "profitRate": 21.5,
  "discountRate": 12.3,
  "items": [
    { "productId": 1, "productName": "노트북", "quantity": 2, "unitPrice": 1000000 }
  ]
}
```
- `discountRate`는 Quote에 별도 필드가 없으므로 `discountAmount / subtotal * 100`으로 계산해서 저장한다.

---

## 전체 흐름

**스냅샷 캡처 (쓰기 경로)**
```
ApprovalService.reject(...)
  → approvalRequest.reject(...)
  → saveHistory(..., action=REJECTED, ...)
        └─ quoteSnapshot = captureSnapshot(quote)  ← 이번에 추가

ApprovalService.reRequest(...)
  → approvalRequest.reRequest(...)   (영업사원이 견적을 이미 수정 완료한 뒤 호출)
  → saveHistory(..., action=RE_REQUESTED, ...)
        └─ quoteSnapshot = captureSnapshot(quote)  ← 이번에 추가
```

**Diff 조회 (읽기 경로)**
```
GET /api/{admin,manager}/approval-requests/{id}
        │
ApprovalService.getApprovalDetail(...)
        │
histories 목록에서 최근 REJECTED → 그 다음 RE_REQUESTED 쌍 탐색
        │
있으면: 두 snapshot JSON을 파싱해 QuoteDiffResponse 계산 (저장하지 않고 매번 계산)
없으면: quoteDiff = null
        │
ApprovalRequestDetailResponse.quoteDiff 필드로 반환
```

---

## API 응답 추가 필드

`ApprovalRequestDetailResponse`에 필드 추가:

```json
{
  "quoteDiff": {
    "totalAmountBefore": 10000000, "totalAmountAfter": 12000000,
    "profitRateBefore": 18.0, "profitRateAfter": 21.5,
    "discountRateBefore": 15.0, "discountRateAfter": 12.3,
    "addedItems": [ { "productName": "마우스", "quantity": 5 } ],
    "removedItems": [],
    "quantityChangedItems": [ { "productName": "노트북", "before": 1, "after": 2 } ]
  }
}
```
`quoteDiff`가 `null`이면 프론트는 "변경 항목" 섹션을 렌더링하지 않는다.

---

## 새로 만들 파일 / 수정할 파일

| 파일 | 변경 내용 |
|------|---------|
| `domain/approval/entity/QuoteApprovalHistory.java` | `quoteSnapshot` 필드 추가 |
| `domain/approval/dto/QuoteSnapshotDto.java` (신규) | 스냅샷 JSON 직렬화/역직렬화용 DTO |
| `domain/approval/dto/response/QuoteDiffResponse.java` (신규) | diff 응답 DTO + 두 스냅샷 비교 정적 팩토리 메서드 |
| `domain/approval/dto/response/ApprovalRequestDetailResponse.java` | `quoteDiff` 필드 추가 |
| `domain/approval/service/ApprovalService.java` | `captureSnapshot(Quote)` 헬퍼 추가, `reject`/`reRequest`의 `saveHistory` 호출에 스냅샷 연결, 상세 조회 시 diff 계산 |
| `sql/QuoteGuard.sql`, `QuoteGuard.sql`(루트) | `quote_approval_histories`에 `quote_snapshot` 컬럼 추가 |

---

## 엣지 케이스

- **최초 요청(반려 이력 없음):** `quoteDiff: null`
- **여러 차례 재요청:** 가장 최근 REJECTED↔RE_REQUESTED 쌍만 비교, 그 이전 이력은 무시
- **REJECTED는 있는데 그 뒤 RE_REQUESTED가 아직 없는 경우(재요청 전):** `quoteDiff: null` (재요청이 일어나야 비교 대상이 생김)
- **과거 데이터(이번 배포 이전에 이미 반려·재요청된 건):** 스냅샷이 없어 자동으로 `quoteDiff: null` — 별도 마이그레이션/백필 없음
- **JSON 파싱 실패:** diff 계산을 건너뛰고 `quoteDiff: null` 반환, 로그만 남김 (승인 상세 조회 자체가 실패하면 안 됨)

---

## 테스트 계획

- `QuoteApprovalHistory`/`ApprovalService` 관련 테스트에 스냅샷 저장 확인 추가 (반려/재요청 시 `quoteSnapshot`이 채워지는지)
- `QuoteDiffResponse`의 두 스냅샷 비교 로직 단위 테스트: 금액/이익률/할인율 증감, 품목 추가/삭제/수량변경 각각
- 반려 이력 없음 → `quoteDiff` null 테스트
- 여러 차례 재요청 시 가장 최근 쌍만 비교하는지 테스트
