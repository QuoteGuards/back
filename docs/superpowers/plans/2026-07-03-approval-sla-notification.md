# 승인 대기 SLA 초과 알림 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PENDING 상태로 SLA(기본 2일)를 초과한 승인 요청을 매일 담당 영업관리자(SALES_MANAGER) + 전체 최고관리자(SUPER_ADMIN)에게 인앱 알림으로 발송한다.

**Architecture:** 기존 `QuoteReminderScheduler`/`QuoteService.notifyExpiringQuotes()` 패턴을 따라, 매일 09:00에 실행되는 얇은 스케줄러(`ApprovalSlaScheduler`)가 `ApprovalService.notifySlaBreaches(int slaDays)`를 호출한다. 서비스는 SLA를 초과한 `PENDING` 승인 요청을 조회하고, 기존 `notifyApprovers()`가 쓰던 "담당자 결정" 로직을 `resolveApprovers()`로 추출해 재사용하여 `NotificationCreateEvent`를 발행한다. SLA 기준일은 `application.properties`의 `approval.sla.days`(기본 2)에서 스케줄러가 읽어 서비스 메서드에 파라미터로 넘긴다 — `@Value` 필드를 서비스에 직접 두면 순수 단위 테스트(`new ApprovalService(...)`)에서 값이 주입되지 않아 테스트가 어려워지기 때문에, 스케줄러(테스트하지 않는 얇은 계층)에서만 `@Value`를 쓰고 서비스는 파라미터로 받는다.

**Tech Stack:** Spring Boot (`@Scheduled`, `@TransactionalEventListener`), Spring Data JPA (`@Query`), JUnit5 + Mockito.

## Global Constraints

- SLA 기준일: `application.properties`의 `approval.sla.days` (기본값 `2`)
- 알림 대상: 요청자가 SALES_STAFF → 같은 부서 SALES_MANAGER 전원 + 전체 SUPER_ADMIN / 요청자가 SALES_MANAGER → 전체 SUPER_ADMIN만 (요청자 본인 제외)
- 채널: 인앱 알림(`NotificationCreateEvent` → SSE)만. 이메일 발송 없음.
- 재발송 정책: 별도 dedup 플래그 없이, 매일 스케줄러 실행 시점의 조건으로 재계산 (상태가 바뀌면 자동으로 대상에서 빠짐)
- 스펙 문서: `docs/superpowers/specs/2026-07-03-approval-sla-notification-design.md`

---

### Task 1: NotificationType, application.properties, Repository 조회 메서드

**Files:**
- Modify: `src/main/java/com/project/back/notification/entity/NotificationType.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/project/back/domain/approval/repository/ApprovalRequestRepository.java`

**Interfaces:**
- Produces: `NotificationType.APPROVAL_SLA_BREACH` (enum 상수), `ApprovalRequestRepository.findPendingRequestedBefore(LocalDateTime threshold): List<ApprovalRequest>` (quote, requester `JOIN FETCH`)

이 태스크는 기존 값 추가/설정 추가/단순 조회 쿼리 추가로, 이 프로젝트에는 리포지토리 단위 테스트 관례가 없다 (다른 `ApprovalRequestRepository` 메서드들도 테스트 없이 `ApprovalService` 목 테스트로만 검증됨). 따라서 컴파일 확인으로 검증한다.

- [ ] **Step 1: NotificationType에 새 타입 추가**

`src/main/java/com/project/back/notification/entity/NotificationType.java` 전체를 아래로 교체:

```java
package com.project.back.notification.entity;

public enum NotificationType {
    USER_CREATED,
    USER_SUSPENDED,
    USER_REACTIVATED,
    PASSWORD_RESET,
    ROLE_CHANGED,
    APPROVAL_REQUESTED,
    APPROVAL_SLA_BREACH,
    QUOTE_APPROVED,
    QUOTE_REJECTED,
    QUOTE_EXPIRING,
    EMAIL_SENT,
    EMAIL_FAILED,
    SYSTEM
}
```

- [ ] **Step 2: application.properties에 SLA 기준일 설정 추가**

`src/main/resources/application.properties` 파일 맨 끝(71번째 줄, `app.initial-password-token-expiry-minutes=1440` 다음)에 아래 추가:

```properties

# Approval
approval.sla.days=2
```

- [ ] **Step 3: 리포지토리에 SLA 초과 대상 조회 메서드 추가**

`src/main/java/com/project/back/domain/approval/repository/ApprovalRequestRepository.java`에서 마지막 `search(...)` 메서드 뒤, 클로징 `}` 앞에 추가:

```java

    // SLA 초과 대상 조회 — PENDING 상태이면서 요청일이 threshold 이전인 건 (스케줄러가 매일 호출)
    @Query("""
        SELECT ar FROM ApprovalRequest ar
        JOIN FETCH ar.quote
        JOIN FETCH ar.requester
        WHERE ar.status = 'PENDING'
        AND ar.requestedAt <= :threshold
        """)
    List<ApprovalRequest> findPendingRequestedBefore(@Param("threshold") LocalDateTime threshold);
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileJava -q`
Expected: 에러 없이 종료 (출력 없음)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/project/back/notification/entity/NotificationType.java src/main/resources/application.properties src/main/java/com/project/back/domain/approval/repository/ApprovalRequestRepository.java
git commit -m "feat(approval): SLA 초과 알림을 위한 타입/설정/조회 쿼리 추가"
```

---

### Task 2: notifyApprovers() 담당자 결정 로직을 resolveApprovers()로 추출 (리팩터)

**Files:**
- Modify: `src/main/java/com/project/back/domain/approval/service/ApprovalService.java:113-138` (기존 `notifyApprovers` 메서드)
- Test: `src/test/java/com/project/back/domain/approval/service/ApprovalServiceTest.java` (기존 `requestApproval` 관련 테스트 — 변경 없이 그대로 통과해야 함)

**Interfaces:**
- Consumes: `UserRepository.findByRoleAndStatus(UserRole, UserStatus): List<User>`, `UserRepository.findByRoleAndDepartmentAndStatus(UserRole, String, UserStatus): List<User>` (기존 그대로)
- Produces: `resolveApprovers(User requester): List<User>` — Task 3에서 재사용

동작을 바꾸지 않는 순수 리팩터이므로, 기존 테스트가 리팩터 전후로 계속 통과하는 것 자체가 회귀 테스트 역할을 한다.

- [ ] **Step 1: 리팩터 전 기존 테스트가 통과하는지 기준선 확인**

Run: `./gradlew test --tests "com.project.back.domain.approval.service.ApprovalServiceTest" -q`
Expected: 실패 없이 종료 (BUILD SUCCESSFUL)

- [ ] **Step 2: notifyApprovers()에서 담당자 결정 로직을 resolveApprovers()로 추출**

`src/main/java/com/project/back/domain/approval/service/ApprovalService.java`에서 기존 `notifyApprovers` 메서드 전체를 아래로 교체:

```java
    /**
     * 승인 요청 대상자에게 알림을 발송한다.
     * - 요청자가 영업사원이면: 같은 부서 영업관리자 + 전체 최고관리자
     * - 요청자가 영업관리자면: 전체 최고관리자
     */
    private void notifyApprovers(User requester, Quote quote, Long approvalRequestId) {
        String title = "새 승인 요청";
        String message = requester.getName() + "님이 견적 " + quote.getQuoteNumber() + " 승인을 요청했습니다.";

        for (User approver : resolveApprovers(requester)) {
            eventPublisher.publishEvent(new NotificationCreateEvent(
                    approver.getId(),
                    NotificationType.APPROVAL_REQUESTED,
                    title,
                    message,
                    NotificationRelatedType.APPROVAL,
                    approvalRequestId));
        }
    }

    /**
     * 승인 권한자(담당자) 목록을 결정한다.
     * - 요청자가 SALES_STAFF: 같은 부서 SALES_MANAGER 전원 + 전체 SUPER_ADMIN
     * - 요청자가 SALES_MANAGER: 전체 SUPER_ADMIN만
     * - 요청자 본인은 결과에서 제외 (본인이 SUPER_ADMIN 등으로 포함될 수 있으므로)
     */
    private List<User> resolveApprovers(User requester) {
        List<User> approvers = new java.util.ArrayList<>(
                userRepository.findByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE));

        if (requester.getRole() == UserRole.SALES_STAFF && requester.getDepartment() != null) {
            approvers.addAll(userRepository.findByRoleAndDepartmentAndStatus(
                    UserRole.SALES_MANAGER, requester.getDepartment(), UserStatus.ACTIVE));
        }

        approvers.removeIf(approver -> approver.getId().equals(requester.getId()));
        return approvers;
    }
```

- [ ] **Step 3: 리팩터 후 기존 테스트가 그대로 통과하는지 확인**

Run: `./gradlew test --tests "com.project.back.domain.approval.service.ApprovalServiceTest" -q`
Expected: 실패 없이 종료 (BUILD SUCCESSFUL) — Step 1과 동일한 결과여야 함

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/project/back/domain/approval/service/ApprovalService.java
git commit -m "refactor(approval): 승인 담당자 결정 로직을 resolveApprovers()로 추출"
```

---

### Task 3: ApprovalService.notifySlaBreaches() 구현

**Files:**
- Modify: `src/main/java/com/project/back/domain/approval/service/ApprovalService.java`
- Test: `src/test/java/com/project/back/domain/approval/service/ApprovalServiceTest.java`

**Interfaces:**
- Consumes: `ApprovalRequestRepository.findPendingRequestedBefore(LocalDateTime): List<ApprovalRequest>` (Task 1), `resolveApprovers(User): List<User>` (Task 2)
- Produces: `ApprovalService.notifySlaBreaches(int slaDays): void` — Task 4의 스케줄러가 호출

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/project/back/domain/approval/service/ApprovalServiceTest.java`에서 마지막 `@Nested` 클래스(`GetPendingListTests`) 뒤, 파일 마지막 `}` 앞에 추가:

```java

    @Nested
    @DisplayName("notifySlaBreaches - SLA 초과 승인 요청 알림")
    class NotifySlaBreachesTests {

        @Test
        @DisplayName("SLA를 초과한 건이 없으면 알림을 발행하지 않는다")
        void notifySlaBreaches_noTargets_doesNothing() {
            when(approvalRequestRepository.findPendingRequestedBefore(any())).thenReturn(List.of());

            service.notifySlaBreaches(2);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("요청자가 SALES_STAFF면 같은 부서 SALES_MANAGER 전원 + 전체 SUPER_ADMIN에게 알림을 발행한다")
        void notifySlaBreaches_staffRequester_notifiesDeptManagersAndAllAdmins() {
            User staff = User.builder()
                    .id(1L).name("홍길동").role(UserRole.SALES_STAFF).department("영업1팀")
                    .build();
            User manager = User.builder()
                    .id(2L).name("김관리").role(UserRole.SALES_MANAGER).department("영업1팀")
                    .build();
            User admin = User.builder()
                    .id(3L).name("박최고").role(UserRole.SUPER_ADMIN)
                    .build();

            Quote quote = mock(Quote.class);
            when(quote.getQuoteNumber()).thenReturn("Q-2026-0001");

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(10L).quote(quote).requester(staff)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestedAt(LocalDateTime.now().minusDays(5))
                    .build();

            when(approvalRequestRepository.findPendingRequestedBefore(any()))
                    .thenReturn(List.of(approvalRequest));
            when(userRepository.findByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE))
                    .thenReturn(List.of(admin));
            when(userRepository.findByRoleAndDepartmentAndStatus(UserRole.SALES_MANAGER, "영업1팀", UserStatus.ACTIVE))
                    .thenReturn(List.of(manager));

            service.notifySlaBreaches(2);

            ArgumentCaptor<NotificationCreateEvent> captor = ArgumentCaptor.forClass(NotificationCreateEvent.class);
            verify(eventPublisher, times(2)).publishEvent(captor.capture());

            List<Long> recipientIds = captor.getAllValues().stream()
                    .map(NotificationCreateEvent::userId).toList();
            assertThat(recipientIds).containsExactlyInAnyOrder(2L, 3L);

            NotificationCreateEvent event = captor.getAllValues().get(0);
            assertThat(event.type()).isEqualTo(com.project.back.notification.entity.NotificationType.APPROVAL_SLA_BREACH);
            assertThat(event.relatedType()).isEqualTo(com.project.back.notification.entity.NotificationRelatedType.APPROVAL);
            assertThat(event.relatedId()).isEqualTo(10L);
            assertThat(event.message()).contains("Q-2026-0001").contains("5일째");
        }

        @Test
        @DisplayName("요청자가 SALES_MANAGER면 전체 SUPER_ADMIN에게만 알림을 발행한다")
        void notifySlaBreaches_managerRequester_notifiesOnlyAdmins() {
            User managerRequester = User.builder()
                    .id(4L).name("김관리").role(UserRole.SALES_MANAGER).department("영업1팀")
                    .build();
            User admin = User.builder()
                    .id(3L).name("박최고").role(UserRole.SUPER_ADMIN)
                    .build();

            Quote quote = mock(Quote.class);
            when(quote.getQuoteNumber()).thenReturn("Q-2026-0002");

            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .id(11L).quote(quote).requester(managerRequester)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .requestedAt(LocalDateTime.now().minusDays(3))
                    .build();

            when(approvalRequestRepository.findPendingRequestedBefore(any()))
                    .thenReturn(List.of(approvalRequest));
            when(userRepository.findByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE))
                    .thenReturn(List.of(admin));

            service.notifySlaBreaches(2);

            ArgumentCaptor<NotificationCreateEvent> captor = ArgumentCaptor.forClass(NotificationCreateEvent.class);
            verify(eventPublisher, times(1)).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(3L);
            verify(userRepository, never())
                    .findByRoleAndDepartmentAndStatus(eq(UserRole.SALES_MANAGER), any(), any());
        }
    }
```

**주의:** 이 테스트는 `User.builder()`를 직접 사용한다. `User` 엔티티가 `@Builder`를 지원하는지, `id` 필드를 빌더로 직접 설정할 수 있는지 아래 명령으로 미리 확인해라:

Run: `grep -n "@Builder\|@AllArgsConstructor" src/main/java/com/project/back/domain/user/entity/User.java`
Expected: `@Builder`와 `@AllArgsConstructor(access = AccessLevel.PRIVATE)`가 출력됨 (즉 `User.builder().id(1L)...build()`가 가능함)

- [ ] **Step 2: 테스트 실행 → 컴파일 실패로 인한 실패 확인**

Run: `./gradlew compileTestJava -q`
Expected: FAIL — `cannot find symbol: method notifySlaBreaches(int)` (아직 서비스에 메서드가 없으므로)

- [ ] **Step 3: ApprovalService에 notifySlaBreaches() 최소 구현**

`src/main/java/com/project/back/domain/approval/service/ApprovalService.java` 상단 import 블록에 추가 (`import java.time.LocalDate;` 바로 위):

```java
import java.time.temporal.ChronoUnit;
```

클래스 선언부에 `@Slf4j` 추가 (기존 `@Service` 위):

```java
import lombok.extern.slf4j.Slf4j;
```

```java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {
```

`notifyApprovers` 메서드 앞(또는 `resolveApprovers` 메서드 뒤)에 추가:

```java

    // ── SLA 초과 승인 요청 알림 (스케줄러에서 매일 호출) ──
    public void notifySlaBreaches(int slaDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(slaDays);
        List<ApprovalRequest> breaches = approvalRequestRepository.findPendingRequestedBefore(threshold);

        for (ApprovalRequest approvalRequest : breaches) {
            try {
                notifySlaBreach(approvalRequest);
            } catch (Exception e) {
                log.warn("SLA 초과 알림 생성 실패 - approvalRequestId={}", approvalRequest.getId(), e);
            }
        }
    }

    private void notifySlaBreach(ApprovalRequest approvalRequest) {
        User requester = approvalRequest.getRequester();
        Quote quote = approvalRequest.getQuote();
        long daysPending = ChronoUnit.DAYS.between(approvalRequest.getRequestedAt(), LocalDateTime.now());

        String title = "승인 대기 SLA 초과";
        String message = "견적 " + quote.getQuoteNumber() + " 승인 요청이 " + daysPending + "일째 대기 중입니다.";

        for (User approver : resolveApprovers(requester)) {
            eventPublisher.publishEvent(new NotificationCreateEvent(
                    approver.getId(),
                    NotificationType.APPROVAL_SLA_BREACH,
                    title,
                    message,
                    NotificationRelatedType.APPROVAL,
                    approvalRequest.getId()));
        }
    }
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew test --tests "com.project.back.domain.approval.service.ApprovalServiceTest" -q`
Expected: 실패 없이 종료 (BUILD SUCCESSFUL)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/project/back/domain/approval/service/ApprovalService.java src/test/java/com/project/back/domain/approval/service/ApprovalServiceTest.java
git commit -m "feat(approval): SLA 초과 PENDING 승인 요청 알림 로직 추가"
```

---

### Task 4: ApprovalSlaScheduler 추가 (매일 09:00 트리거)

**Files:**
- Create: `src/main/java/com/project/back/domain/approval/scheduler/ApprovalSlaScheduler.java`

**Interfaces:**
- Consumes: `ApprovalService.notifySlaBreaches(int slaDays)` (Task 3)

이 프로젝트에는 스케줄러 클래스 단위 테스트 관례가 없다 (`QuoteReminderScheduler`도 테스트 없음) — cron 트리거 자체는 스프링이 보장하는 영역이고, 실제 로직은 이미 Task 3에서 검증됐기 때문이다. 컴파일 확인 + 기동 확인으로 검증한다.

- [ ] **Step 1: 스케줄러 클래스 작성**

`src/main/java/com/project/back/domain/approval/scheduler/ApprovalSlaScheduler.java` 신규 생성:

```java
package com.project.back.domain.approval.scheduler;

import com.project.back.domain.approval.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalSlaScheduler {

    private final ApprovalService approvalService;

    @Value("${approval.sla.days:2}")
    private int slaDays;

    // 매일 오전 9시 실행 — PENDING 상태로 SLA(기본 2일)를 초과한 승인 요청을 담당자에게 알린다.
    @Scheduled(cron = "0 0 9 * * *")
    public void notifySlaBreaches() {
        log.info("승인 SLA 초과 알림 스케줄러 시작 [기준일={}일]", slaDays);
        approvalService.notifySlaBreaches(slaDays);
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava -q`
Expected: 에러 없이 종료 (출력 없음)

- [ ] **Step 3: 전체 테스트 스위트 실행 (회귀 확인)**

Run: `./gradlew test -q`
Expected: 실패 없이 종료 (BUILD SUCCESSFUL)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/project/back/domain/approval/scheduler/ApprovalSlaScheduler.java
git commit -m "feat(approval): 매일 09시 승인 SLA 초과 알림 스케줄러 추가"
```

---

## 완료 후 확인 사항 (수동, 배포 전)

- 로컬에서 애플리케이션을 기동해 `ApprovalSlaScheduler` 빈 생성 시 에러가 없는지 확인 (`@Value` 프로퍼티 미설정 시에도 기본값 2가 적용되는지)
- SUPER_ADMIN 또는 SALES_MANAGER 계정으로 로그인해 실제 알림 벨(NotificationBell)에 "승인 대기 SLA 초과" 알림이 SSE로 수신되는지 수동 확인 (테스트 DB에서 `requestedAt`을 3일 전으로 직접 UPDATE한 PENDING 건 하나 만들어서 스케줄러 메서드를 수동 호출하거나, cron을 임시로 앞당겨 확인)
