# 승인 대기 SLA 초과 알림 설계

**날짜:** 2026-07-03
**담당:** 승인/반려 파트
**대상 브랜치:** feature/approval

---

## 개요

`PENDING` 상태로 일정 기간(SLA) 이상 방치된 승인 요청을, 매일 담당 영업관리자(SALES_MANAGER)와 전체 최고관리자(SUPER_ADMIN)에게 인앱 알림으로 알린다.

**배경:** 기존 `QuoteReminderScheduler`는 임시저장(DRAFT) 견적만 리마인드하고, 승인 대기 건에는 SLA 개념이 없다. 승인이 며칠씩 방치돼도 아무도 모르는 상태라 승인 병목의 원인이 된다.

**이번 스펙의 범위:** 알림 발송까지만. 목록 화면 "대기 N일" 뱃지·정렬과 대시보드 KPI(평균 처리 시간, SLA 초과 건수)는 이후 별도 스펙으로 분리한다 (알림을 먼저 운영해보고 SLA 기준일이 적절한지 검증한 뒤 진행).

---

## 요구사항

- **SLA 기준일:** `application.properties`의 `approval.sla.days` (기본값 2일)
- **알림 대상:** 요청자가 SALES_STAFF면 같은 부서 SALES_MANAGER 전원 + 전체 SUPER_ADMIN, 요청자가 SALES_MANAGER면 전체 SUPER_ADMIN만 (본인 제외). 기존 `notifyApprovers()`의 수신자 결정 로직을 재사용한다.
- **발송 주기:** 매일 1회(오전 9시). 상태가 바뀔 때까지(승인/반려/취소) 매일 반복 알림. 별도의 "이미 보냈는지" 추적 플래그는 두지 않는다 — 매일 조건을 다시 계산하기 때문에 상태가 바뀌면 자동으로 대상에서 빠진다.
- **채널:** 이번 스펙은 인앱 알림(SSE)만. 이메일은 범위 밖.

---

## 전체 흐름

```
[Spring @Scheduled, 매일 09:00]
        │
[ApprovalSlaScheduler.notifySlaBreaches()]
        │
[ApprovalService.notifySlaBreaches()]
        │
  ApprovalRequestRepository.findPendingRequestedBefore(threshold)
  (threshold = now - approval.sla.days)
        │
  각 ApprovalRequest 마다:
        │
    ├─ daysPending 계산 (requestedAt ~ now)
    ├─ resolveApprovers(requester)  ← 기존 notifyApprovers()에서 추출한 공통 로직
    └─ 수신자별 NotificationCreateEvent 발행
              │
        (AFTER_COMMIT) NotificationEventListener
              │
        NotificationService.create() → DB 저장 + SSE 전송
```

한 건 처리 중 예외가 나도 로그만 남기고 다음 건을 계속 처리한다 (`QuoteReminderScheduler`와 동일한 방식).

---

## 알림 내용

| 필드 | 값 |
|------|-----|
| type | `APPROVAL_SLA_BREACH` (신규) |
| title | "승인 대기 SLA 초과" |
| message | "견적 {quoteNumber} 승인 요청이 {daysPending}일째 대기 중입니다." |
| relatedType | `APPROVAL` |
| relatedId | `approvalRequest.getId()` |

---

## 새로 만들 파일

| 파일 | 역할 |
|------|------|
| `domain/approval/scheduler/ApprovalSlaScheduler.java` | 매일 09:00 트리거, `ApprovalService.notifySlaBreaches()` 호출만 담당 |

## 수정할 파일

| 파일 | 변경 내용 |
|------|---------|
| `notification/entity/NotificationType.java` | `APPROVAL_SLA_BREACH` 추가 |
| `domain/approval/repository/ApprovalRequestRepository.java` | `findPendingRequestedBefore(LocalDateTime threshold)` 추가 (quote, requester `JOIN FETCH`) |
| `domain/approval/service/ApprovalService.java` | ① `notifyApprovers()`의 수신자 결정 로직을 `resolveApprovers(User requester)`로 추출 (기존 동작 변경 없음, 순수 리팩터링) ② `notifySlaBreaches()` 신규 추가 |
| `src/main/resources/application.properties` | `approval.sla.days=2` 추가 |

---

## 엣지 케이스

- **같은 날 스케줄러가 중복 실행되는 경우는 없음** — cron이 하루 1회만 트리거. 별도 dedup 로직 불필요.
- **재요청(reRequest) 후:** `requestedAt`이 갱신되므로 SLA 카운트가 재요청 시점부터 다시 시작된다 (기존 `reRequest()`가 이미 `requestedAt = now`로 갱신함).
- **승인자가 없는 부서(SALES_MANAGER 없음):** 전체 SUPER_ADMIN에게만 발송된다 (기존 `notifyApprovers()`와 동일 동작).
- **알림 저장/SSE 전송 실패:** `NotificationEventListener`가 이미 예외를 흡수하고 로그만 남기므로 스케줄러 쪽에 영향 없음.

---

## 테스트 계획

`ApprovalServiceTest`에 `notifySlaBreaches` 관련 `@Nested` 클래스 추가:

- SLA 기준일을 지난 PENDING 건 → 알림 이벤트 발행됨
- 기준일 이내 PENDING 건 → 알림 이벤트 발행 안 됨
- APPROVED/REJECTED/CANCELLED 건 → 조회 대상에서 제외됨 (리포지토리 쿼리가 PENDING만 조회하므로 서비스 레벨에서 별도 필터링 불필요 — 리포지토리 쿼리 테스트로 검증)
- 요청자가 SALES_STAFF인 경우 → 같은 부서 SALES_MANAGER + 전체 SUPER_ADMIN에게 발송, 본인 제외
- 요청자가 SALES_MANAGER인 경우 → 전체 SUPER_ADMIN에게만 발송

스케줄러의 `@Scheduled` 트리거 자체는 테스트하지 않는다 (cron 동작은 스프링이 보장하는 영역이고, 검증 대상은 트리거 시점에 호출되는 로직의 정확성이다).
