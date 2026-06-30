# AI 리스크 요약 기능 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 영업관리자/최고관리자가 승인 상세 조회 시 Gemini AI가 견적 재무 데이터와 품목을 분석해 리스크를 bullet point로 요약해 반환한다.

**Architecture:** 상세 조회 API는 기존 그대로 유지하고, 별도 엔드포인트(`GET /api/.../ai-summary`)를 추가한다. `AiRiskSummaryService`가 DB 캐시를 먼저 확인하고, 없으면 `GeminiClient`를 통해 Gemini API를 호출 후 저장한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring `RestClient` (추가 의존성 없음), Google Gemini API (gemini-2.0-flash), JPA/Hibernate, JUnit 5 + Mockito

## Global Constraints

- Java 21, Spring Boot 4.1.0 사용
- 추가 외부 라이브러리 의존성 금지 (RestClient 사용)
- 모든 예외는 `CustomException(ErrorCode)` 형태로 던질 것
- 기존 `ApprovalService` 수정 금지 — 새 서비스 파일 분리
- Gemini 모델: `gemini-2.0-flash`
- 테스트는 H2 in-memory + Mockito 사용

---

## 파일 구조

**새로 만들 파일**

| 파일 경로 | 역할 |
|-----------|------|
| `src/main/java/com/project/back/global/client/GeminiClient.java` | Gemini API HTTP 호출 |
| `src/main/java/com/project/back/global/client/dto/GeminiRequest.java` | Gemini 요청 record |
| `src/main/java/com/project/back/global/client/dto/GeminiResponse.java` | Gemini 응답 record |
| `src/main/java/com/project/back/domain/approval/service/AiRiskSummaryService.java` | 캐시 확인 → 프롬프트 조립 → 호출 → 저장 |
| `src/main/java/com/project/back/domain/approval/dto/response/AiRiskSummaryResponse.java` | 응답 DTO |
| `src/test/java/com/project/back/domain/approval/service/AiRiskSummaryServiceTest.java` | 서비스 단위 테스트 |

**수정할 파일**

| 파일 경로 | 변경 내용 |
|-----------|---------|
| `src/main/java/com/project/back/domain/approval/entity/ApprovalRequest.java` | `updateAiRiskSummary(String)` 메서드 추가 |
| `src/main/java/com/project/back/global/exception/ErrorCode.java` | `AI_SUMMARY_GENERATION_FAILED` 추가 |
| `src/main/java/com/project/back/domain/approval/controller/ApprovalController.java` | ai-summary 엔드포인트 2개 추가 |

---

### Task 1: ErrorCode 추가 + ApprovalRequest 업데이트 메서드 추가

**Files:**
- Modify: `src/main/java/com/project/back/global/exception/ErrorCode.java`
- Modify: `src/main/java/com/project/back/domain/approval/entity/ApprovalRequest.java`

**Interfaces:**
- Produces: `ErrorCode.AI_SUMMARY_GENERATION_FAILED` — 이후 Task에서 GeminiClient 실패 시 사용
- Produces: `approvalRequest.updateAiRiskSummary(String summary)` — Task 4에서 저장 시 사용

- [ ] **Step 1: ErrorCode에 AI 에러 코드 추가**

`ErrorCode.java`의 `// Approval` 섹션 끝에 아래 항목 추가:

```java
// Approval
APPROVAL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "APPROVAL_001", "승인 요청을 찾을 수 없습니다."),
APPROVAL_ALREADY_PENDING(HttpStatus.CONFLICT, "APPROVAL_002", "이미 승인 대기 중인 요청이 있습니다."),
APPROVAL_NOT_PENDING(HttpStatus.BAD_REQUEST, "APPROVAL_003", "승인 대기 상태의 요청만 처리할 수 있습니다."),
APPROVAL_NOT_REJECTED(HttpStatus.BAD_REQUEST, "APPROVAL_004", "반려된 견적만 재요청할 수 있습니다."),
APPROVAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "APPROVAL_005", "본인이 요청한 승인 건만 재요청할 수 있습니다."),
REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "APPROVAL_006", "반려 사유는 필수입니다."),
APPROVAL_DEPT_MISMATCH(HttpStatus.FORBIDDEN, "APPROVAL_007", "담당 부서의 승인 요청만 조회할 수 있습니다."),
APPROVAL_SELF_DENIED(HttpStatus.FORBIDDEN, "APPROVAL_008", "자신이 요청한 견적은 직접 승인/반려할 수 없습니다."),
AI_SUMMARY_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "APPROVAL_009", "AI 리스크 요약 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
```

- [ ] **Step 2: ApprovalRequest에 updateAiRiskSummary 메서드 추가**

`ApprovalRequest.java`의 `// ── 상태 변경 메서드 ──` 블록 안에 아래 메서드 추가:

```java
public void updateAiRiskSummary(String summary) {
    this.aiRiskSummary = summary;
}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/project/back/global/exception/ErrorCode.java
git add src/main/java/com/project/back/domain/approval/entity/ApprovalRequest.java
git commit -m "feat: AI 리스크 요약 ErrorCode 추가 및 ApprovalRequest 업데이트 메서드 추가"
```

---

### Task 2: AiRiskSummaryResponse DTO 생성

**Files:**
- Create: `src/main/java/com/project/back/domain/approval/dto/response/AiRiskSummaryResponse.java`

**Interfaces:**
- Produces: `AiRiskSummaryResponse(Long approvalRequestId, String aiRiskSummary, boolean cached)` — Task 4, 5에서 반환 타입으로 사용

- [ ] **Step 1: AiRiskSummaryResponse 파일 생성**

```java
package com.project.back.domain.approval.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AiRiskSummaryResponse {

    private final Long approvalRequestId;
    private final String aiRiskSummary;
    private final boolean cached;

    public static AiRiskSummaryResponse cached(Long approvalRequestId, String summary) {
        return new AiRiskSummaryResponse(approvalRequestId, summary, true);
    }

    public static AiRiskSummaryResponse generated(Long approvalRequestId, String summary) {
        return new AiRiskSummaryResponse(approvalRequestId, summary, false);
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/project/back/domain/approval/dto/response/AiRiskSummaryResponse.java
git commit -m "feat: AiRiskSummaryResponse DTO 추가"
```

---

### Task 3: Gemini 요청/응답 DTO + GeminiClient 생성

**Files:**
- Create: `src/main/java/com/project/back/global/client/dto/GeminiRequest.java`
- Create: `src/main/java/com/project/back/global/client/dto/GeminiResponse.java`
- Create: `src/main/java/com/project/back/global/client/GeminiClient.java`

**Interfaces:**
- Consumes: `application.properties`의 `gemini.api-key` 값
- Produces: `GeminiClient.generateContent(String prompt): String` — Task 4의 `AiRiskSummaryService`에서 호출

- [ ] **Step 1: GeminiRequest record 생성**

`src/main/java/com/project/back/global/client/dto/GeminiRequest.java`:

```java
package com.project.back.global.client.dto;

import java.util.List;

public record GeminiRequest(List<Content> contents) {

    public record Content(List<Part> parts) {}

    public record Part(String text) {}

    public static GeminiRequest of(String prompt) {
        return new GeminiRequest(
                List.of(new Content(List.of(new Part(prompt))))
        );
    }
}
```

- [ ] **Step 2: GeminiResponse record 생성**

`src/main/java/com/project/back/global/client/dto/GeminiResponse.java`:

```java
package com.project.back.global.client.dto;

import java.util.List;

public record GeminiResponse(List<Candidate> candidates) {

    public record Candidate(Content content) {}

    public record Content(List<Part> parts) {}

    public record Part(String text) {}

    public String extractText() {
        return candidates.get(0).content().parts().get(0).text();
    }
}
```

- [ ] **Step 3: GeminiClient 생성**

`src/main/java/com/project/back/global/client/GeminiClient.java`:

```java
package com.project.back.global.client;

import com.project.back.global.client.dto.GeminiRequest;
import com.project.back.global.client.dto.GeminiResponse;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class GeminiClient {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private final RestClient restClient;
    private final String apiKey;

    public GeminiClient(@Value("${gemini.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create();
    }

    public String generateContent(String prompt) {
        try {
            GeminiResponse response = restClient.post()
                    .uri(BASE_URL + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiRequest.of(prompt))
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
            }

            return response.extractText();

        } catch (RestClientException e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED);
        }
    }
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/project/back/global/client/
git commit -m "feat: GeminiClient 및 요청/응답 DTO 추가"
```

---

### Task 4: AiRiskSummaryService 생성 (TDD)

**Files:**
- Create: `src/main/java/com/project/back/domain/approval/service/AiRiskSummaryService.java`
- Create: `src/test/java/com/project/back/domain/approval/service/AiRiskSummaryServiceTest.java`

**Interfaces:**
- Consumes:
  - `ApprovalRequestRepository.findByIdWithUsers(Long id): Optional<ApprovalRequest>`
  - `QuoteItemRepository.findByQuoteIdOrderBySortOrderAsc(Long quoteId): List<QuoteItem>`
  - `QuoteApprovalReasonRepository.findByQuote_Id(Long quoteId): List<QuoteApprovalReason>`
  - `GeminiClient.generateContent(String prompt): String`
  - `ApprovalRequest.updateAiRiskSummary(String summary)`
  - `ApprovalRequest.getAiRiskSummary(): String` (기존 Lombok getter)
- Produces:
  - `AiRiskSummaryService.getSummary(Long approvalRequestId): AiRiskSummaryResponse` — SUPER_ADMIN용
  - `AiRiskSummaryService.getSummaryForManager(Long approvalRequestId, Long managerId): AiRiskSummaryResponse` — SALES_MANAGER용

- [ ] **Step 1: 실패 테스트 작성**

`src/test/java/com/project/back/domain/approval/service/AiRiskSummaryServiceTest.java`:

```java
package com.project.back.domain.approval.service;

import com.project.back.domain.approval.dto.response.AiRiskSummaryResponse;
import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.approval.repository.ApprovalRequestRepository;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.client.GeminiClient;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AiRiskSummaryService 단위 테스트")
class AiRiskSummaryServiceTest {

    private ApprovalRequestRepository approvalRequestRepository;
    private QuoteItemRepository quoteItemRepository;
    private QuoteApprovalReasonRepository quoteApprovalReasonRepository;
    private UserRepository userRepository;
    private GeminiClient geminiClient;
    private AiRiskSummaryService service;

    @BeforeEach
    void setUp() {
        approvalRequestRepository = mock(ApprovalRequestRepository.class);
        quoteItemRepository = mock(QuoteItemRepository.class);
        quoteApprovalReasonRepository = mock(QuoteApprovalReasonRepository.class);
        userRepository = mock(UserRepository.class);
        geminiClient = mock(GeminiClient.class);
        service = new AiRiskSummaryService(
                approvalRequestRepository,
                quoteItemRepository,
                quoteApprovalReasonRepository,
                userRepository,
                geminiClient
        );
    }

    private ApprovalRequest mockApprovalRequest(String existingSummary) {
        Quote quote = mock(Quote.class);
        when(quote.getId()).thenReturn(1L);
        when(quote.getTotalAmount()).thenReturn(new BigDecimal("12000000"));
        when(quote.getDiscountAmount()).thenReturn(new BigDecimal("3200000"));
        when(quote.getSupplyAmount()).thenReturn(new BigDecimal("8800000"));
        when(quote.getProfitRate()).thenReturn(new BigDecimal("8.20"));
        when(quote.getExpectedProfitAmount()).thenReturn(new BigDecimal("720000"));

        ApprovalRequest request = mock(ApprovalRequest.class);
        when(request.getId()).thenReturn(1L);
        when(request.getAiRiskSummary()).thenReturn(existingSummary);
        when(request.getQuote()).thenReturn(quote);

        return request;
    }

    @Nested
    @DisplayName("getSummary - SUPER_ADMIN")
    class GetSummaryTests {

        @Test
        @DisplayName("이미 저장된 요약이 있으면 Gemini 호출 없이 cached:true 반환")
        void returnsCachedSummary() {
            ApprovalRequest request = mockApprovalRequest("- 기존 요약 내용");
            when(approvalRequestRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(request));

            AiRiskSummaryResponse result = service.getSummary(1L);

            assertThat(result.isCached()).isTrue();
            assertThat(result.getAiRiskSummary()).isEqualTo("- 기존 요약 내용");
            verify(geminiClient, never()).generateContent(anyString());
        }

        @Test
        @DisplayName("저장된 요약이 없으면 Gemini 호출 후 cached:false 반환")
        void generatesNewSummary() {
            ApprovalRequest request = mockApprovalRequest(null);
            when(approvalRequestRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(request));
            when(quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
            when(quoteApprovalReasonRepository.findByQuote_Id(1L)).thenReturn(List.of());
            when(geminiClient.generateContent(anyString())).thenReturn("- 새 요약");

            AiRiskSummaryResponse result = service.getSummary(1L);

            assertThat(result.isCached()).isFalse();
            assertThat(result.getAiRiskSummary()).isEqualTo("- 새 요약");
            verify(geminiClient, times(1)).generateContent(anyString());
            verify(request, times(1)).updateAiRiskSummary("- 새 요약");
        }

        @Test
        @DisplayName("승인 요청이 없으면 APPROVAL_REQUEST_NOT_FOUND 예외")
        void throwsWhenNotFound() {
            when(approvalRequestRepository.findByIdWithUsers(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSummary(99L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_REQUEST_NOT_FOUND);
        }

        @Test
        @DisplayName("Gemini 실패 시 AI_SUMMARY_GENERATION_FAILED 예외")
        void throwsWhenGeminiFails() {
            ApprovalRequest request = mockApprovalRequest(null);
            when(approvalRequestRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(request));
            when(quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
            when(quoteApprovalReasonRepository.findByQuote_Id(1L)).thenReturn(List.of());
            when(geminiClient.generateContent(anyString()))
                    .thenThrow(new CustomException(ErrorCode.AI_SUMMARY_GENERATION_FAILED));

            assertThatThrownBy(() -> service.getSummary(1L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SUMMARY_GENERATION_FAILED);
        }
    }

    @Nested
    @DisplayName("getSummaryForManager - SALES_MANAGER")
    class GetSummaryForManagerTests {

        @Test
        @DisplayName("다른 부서 요청이면 APPROVAL_DEPT_MISMATCH 예외")
        void throwsOnDeptMismatch() {
            User manager = mock(User.class);
            when(manager.getDepartment()).thenReturn("영업1팀");

            User requester = mock(User.class);
            when(requester.getDepartment()).thenReturn("영업2팀");

            ApprovalRequest request = mockApprovalRequest(null);
            when(request.getRequester()).thenReturn(requester);

            when(approvalRequestRepository.findByIdWithUsers(1L)).thenReturn(Optional.of(request));
            when(userRepository.findById(10L)).thenReturn(Optional.of(manager));

            assertThatThrownBy(() -> service.getSummaryForManager(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPROVAL_DEPT_MISMATCH);
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.project.back.domain.approval.service.AiRiskSummaryServiceTest"
```

Expected: FAILED — `AiRiskSummaryService` 클래스가 없어서 컴파일 에러

- [ ] **Step 3: AiRiskSummaryService 구현**

`src/main/java/com/project/back/domain/approval/service/AiRiskSummaryService.java`:

```java
package com.project.back.domain.approval.service;

import com.project.back.domain.approval.dto.response.AiRiskSummaryResponse;
import com.project.back.domain.approval.entity.ApprovalRequest;
import com.project.back.domain.approval.entity.QuoteApprovalReason;
import com.project.back.domain.approval.repository.ApprovalRequestRepository;
import com.project.back.domain.approval.repository.QuoteApprovalReasonRepository;
import com.project.back.domain.quote.entity.Quote;
import com.project.back.domain.quote.entity.QuoteItem;
import com.project.back.domain.quote.repository.QuoteItemRepository;
import com.project.back.domain.user.entity.User;
import com.project.back.domain.user.repository.UserRepository;
import com.project.back.global.client.GeminiClient;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRiskSummaryService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final QuoteItemRepository quoteItemRepository;
    private final QuoteApprovalReasonRepository quoteApprovalReasonRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;

    // SUPER_ADMIN용: 부서 제한 없음
    @Transactional
    public AiRiskSummaryResponse getSummary(Long approvalRequestId) {
        ApprovalRequest approvalRequest = findApprovalRequest(approvalRequestId);
        return generateOrGetCached(approvalRequest);
    }

    // SALES_MANAGER용: 동일 부서만 허용
    @Transactional
    public AiRiskSummaryResponse getSummaryForManager(Long approvalRequestId, Long managerId) {
        ApprovalRequest approvalRequest = findApprovalRequest(approvalRequestId);

        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String managerDept = manager.getDepartment();
        String requesterDept = approvalRequest.getRequester().getDepartment();

        if (managerDept == null || !managerDept.equals(requesterDept)) {
            throw new CustomException(ErrorCode.APPROVAL_DEPT_MISMATCH);
        }

        return generateOrGetCached(approvalRequest);
    }

    private AiRiskSummaryResponse generateOrGetCached(ApprovalRequest approvalRequest) {
        // 이미 생성된 요약이 있으면 캐시 반환
        if (approvalRequest.getAiRiskSummary() != null) {
            return AiRiskSummaryResponse.cached(
                    approvalRequest.getId(),
                    approvalRequest.getAiRiskSummary()
            );
        }

        // 견적 데이터 로딩
        Quote quote = approvalRequest.getQuote();
        List<QuoteItem> items = quoteItemRepository.findByQuoteIdOrderBySortOrderAsc(quote.getId());
        List<QuoteApprovalReason> reasons = quoteApprovalReasonRepository.findByQuote_Id(quote.getId());

        // 프롬프트 조립 및 Gemini 호출
        String prompt = buildPrompt(quote, items, reasons);
        String summary = geminiClient.generateContent(prompt);

        // 저장
        approvalRequest.updateAiRiskSummary(summary);
        approvalRequestRepository.save(approvalRequest);

        return AiRiskSummaryResponse.generated(approvalRequest.getId(), summary);
    }

    private String buildPrompt(Quote quote, List<QuoteItem> items, List<QuoteApprovalReason> reasons) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 B2B 영업 견적 리스크 분석 전문가입니다.\n");
        sb.append("아래 견적 정보를 분석하여 승인자가 빠르게 판단할 수 있도록 ");
        sb.append("bullet point 형식으로 리스크를 요약해 주세요. 한국어로 작성하세요.\n\n");

        sb.append("[견적 요약]\n");
        sb.append("- 총 견적액: ").append(quote.getTotalAmount()).append("원\n");
        sb.append("- 할인금액: ").append(quote.getDiscountAmount()).append("원\n");
        sb.append("- 공급가액: ").append(quote.getSupplyAmount()).append("원\n");
        sb.append("- 이익률: ").append(quote.getProfitRate()).append("%\n");
        sb.append("- 예상 이익: ").append(quote.getExpectedProfitAmount()).append("원\n");

        if (!reasons.isEmpty()) {
            sb.append("- 승인 필요 사유: ");
            sb.append(reasons.stream()
                    .map(r -> r.getReasonMessage())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("없음"));
            sb.append("\n");
        }

        if (!items.isEmpty()) {
            sb.append("\n[품목 상세]\n");
            for (int i = 0; i < items.size(); i++) {
                QuoteItem item = items.get(i);
                sb.append(i + 1).append(". ").append(item.getProductName())
                        .append(" (단가 ").append(item.getUnitPrice())
                        .append(" × ").append(item.getQuantity()).append("개")
                        .append(", 할인율 ").append(item.getDiscountRate()).append("%");
                if (item.getDiscountReason() != null && !item.getDiscountReason().isBlank()) {
                    sb.append(", 할인사유: ").append(item.getDiscountReason());
                }
                sb.append(")\n");
            }
        }

        sb.append("\n[출력 형식]\n");
        sb.append("- 항목명: 수치 (판단 코멘트)\n");
        sb.append("마지막 줄에 종합 의견 한 줄\n");

        return sb.toString();
    }

    private ApprovalRequest findApprovalRequest(Long approvalRequestId) {
        return approvalRequestRepository.findByIdWithUsers(approvalRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_REQUEST_NOT_FOUND));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.project.back.domain.approval.service.AiRiskSummaryServiceTest"
```

Expected: 5개 테스트 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/project/back/domain/approval/service/AiRiskSummaryService.java
git add src/test/java/com/project/back/domain/approval/service/AiRiskSummaryServiceTest.java
git commit -m "feat: AiRiskSummaryService 구현 및 테스트 추가"
```

---

### Task 5: ApprovalController 엔드포인트 추가

**Files:**
- Modify: `src/main/java/com/project/back/domain/approval/controller/ApprovalController.java`

**Interfaces:**
- Consumes: `AiRiskSummaryService.getSummary(Long): AiRiskSummaryResponse`
- Consumes: `AiRiskSummaryService.getSummaryForManager(Long, Long): AiRiskSummaryResponse`

- [ ] **Step 1: ApprovalController에 AiRiskSummaryService 의존성 추가**

`ApprovalController.java`의 필드와 import를 아래와 같이 수정:

기존:
```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApprovalController {

    private final ApprovalService approvalService;
```

변경 후:
```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final AiRiskSummaryService aiRiskSummaryService;
```

import에 아래 추가:
```java
import com.project.back.domain.approval.dto.response.AiRiskSummaryResponse;
import com.project.back.domain.approval.service.AiRiskSummaryService;
```

- [ ] **Step 2: SUPER_ADMIN용 AI 요약 엔드포인트 추가**

기존 `// ── 8. 승인 필요 사유 조회 ──` 블록 위에 추가:

```java
// ── AI 리스크 요약 조회 (SUPER_ADMIN - 전체) ──
// GET /api/admin/approval-requests/{approvalRequestId}/ai-summary
@PreAuthorize("hasRole('SUPER_ADMIN')")
@GetMapping("/admin/approval-requests/{approvalRequestId}/ai-summary")
public ResponseEntity<AiRiskSummaryResponse> getAiSummary(
        @PathVariable Long approvalRequestId
) {
    AiRiskSummaryResponse result = aiRiskSummaryService.getSummary(approvalRequestId);
    return ResponseEntity.ok(result);
}

// ── AI 리스크 요약 조회 (SALES_MANAGER - 동일 부서 영업사원만) ──
// GET /api/manager/approval-requests/{approvalRequestId}/ai-summary
@PreAuthorize("hasRole('SALES_MANAGER')")
@GetMapping("/manager/approval-requests/{approvalRequestId}/ai-summary")
public ResponseEntity<AiRiskSummaryResponse> getAiSummaryForManager(
        @PathVariable Long approvalRequestId,
        @AuthenticationPrincipal Long userId
) {
    AiRiskSummaryResponse result = aiRiskSummaryService.getSummaryForManager(approvalRequestId, userId);
    return ResponseEntity.ok(result);
}
```

- [ ] **Step 3: 전체 테스트 통과 확인**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, 모든 기존 테스트 통과

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/project/back/domain/approval/controller/ApprovalController.java
git commit -m "feat: AI 리스크 요약 엔드포인트 추가 (SUPER_ADMIN, SALES_MANAGER)"
```

---

## 완료 기준 체크리스트

- [ ] `GET /api/admin/approval-requests/{id}/ai-summary` — SUPER_ADMIN 접근 가능
- [ ] `GET /api/manager/approval-requests/{id}/ai-summary` — SALES_MANAGER 동일 부서만 접근
- [ ] 첫 조회 시 Gemini API 호출 → DB 저장 → `cached: false` 반환
- [ ] 두 번째 조회 시 DB에서 반환 → `cached: true` 반환
- [ ] Gemini 실패 시 503 + `AI_SUMMARY_GENERATION_FAILED` 반환
- [ ] 기존 승인 테스트 전부 통과
