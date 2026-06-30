# AI 리스크 요약 기능 설계

**날짜:** 2026-06-30  
**담당:** 승인/반려 파트  
**대상 브랜치:** feature/approval

---

## 개요

영업관리자(SALES_MANAGER) 및 최고관리자(SUPER_ADMIN)가 승인 상세 화면을 열었을 때,  
Gemini AI가 견적의 재무 수치와 품목 상세를 분석해 리스크를 bullet point로 요약한다.

---

## 요구사항

- **생성 시점:** 영업관리자가 승인 상세를 처음 조회할 때 (lazy 방식)
- **출력 형식:** bullet point (항목명: 수치 + 판단 코멘트, 마지막 줄 종합 의견)
- **입력 데이터:** 견적 요약 수치 + 품목별 상세 (제품명, 단가, 수량, 할인율, 할인사유)
- **캐싱:** 한 번 생성된 요약은 DB에 저장 → 이후 조회 시 재호출 없이 반환
- **AI 엔진:** Google Gemini API (`gemini.api-key` 기 설정)

---

## API 명세

### 엔드포인트
```
GET /api/approvals/{approvalRequestId}/ai-summary
```

### 권한
| 역할 | 접근 가능 여부 |
|------|--------------|
| SUPER_ADMIN | 전체 접근 |
| SALES_MANAGER | 동일 부서 영업사원 견적만 |
| SALES_REP | 접근 불가 |

### 응답 (200 OK)
```json
{
  "approvalRequestId": 1,
  "aiRiskSummary": "- 이익률: 8.2% (최소 기준 15% 미달 ⚠️)\n- 할인금액: 3,200,000원 (총액 대비 26.7%, 정책 초과 ⚠️)\n- 총 견적액: 12,000,000원\n종합: 수익성 리스크 높음, 승인 신중 검토 권장",
  "cached": false
}
```

### 에러 응답
| 상황 | 상태코드 | ErrorCode |
|------|---------|-----------|
| Gemini 호출 실패 | 503 | AI_SUMMARY_GENERATION_FAILED |
| 승인 요청 없음 | 404 | APPROVAL_REQUEST_NOT_FOUND |
| 부서 불일치 | 403 | APPROVAL_DEPT_MISMATCH |

---

## 전체 흐름

```
[영업관리자 화면]
  ├─ ① GET /approvals/{id}          → 기존 상세 API (즉시 응답)
  └─ ② GET /approvals/{id}/ai-summary
              │
        [AiRiskSummaryService]
              │
        aiRiskSummary DB에 있음? ─Yes─→ cached:true 반환
              │
             No
              │
        견적 데이터 조회 (Quote + QuoteItems + ApprovalReasons)
              │
        프롬프트 조립
              │
        [GeminiClient] → Gemini API 호출
              │
        ApprovalRequest.aiRiskSummary 저장
              │
        cached:false 반환
```

---

## 프롬프트 설계

```
[시스템 지시]
당신은 B2B 영업 견적 리스크 분석 전문가입니다.
아래 견적 정보를 분석하여 승인자가 빠르게 판단할 수 있도록
bullet point 형식으로 리스크를 요약해 주세요. 한국어로 작성하세요.

[견적 요약]
- 총 견적액: {totalAmount}원
- 할인금액: {discountAmount}원
- 공급가액: {supplyAmount}원
- 이익률: {profitRate}%
- 예상 이익: {expectedProfitAmount}원
- 승인 필요 사유: {reasons}

[품목 상세]
{items: 제품명, 단가 × 수량, 할인율, 할인사유}

[출력 형식]
- 항목명: 수치 (판단 코멘트)
마지막 줄에 종합 의견 한 줄
```

---

## 새로 만들 파일

| 파일 | 역할 |
|------|------|
| `global/client/GeminiClient.java` | Gemini API HTTP 호출 (RestClient 사용, 모델: gemini-2.0-flash) |
| `domain/approval/service/AiRiskSummaryService.java` | 캐시 확인 → 프롬프트 조립 → 호출 → 저장 |
| `domain/approval/dto/response/AiRiskSummaryResponse.java` | 응답 DTO |

## 수정할 파일

| 파일 | 변경 내용 |
|------|---------|
| `ApprovalController.java` | 새 엔드포인트 추가 |
| `global/exception/ErrorCode.java` | `AI_SUMMARY_GENERATION_FAILED` 추가 |
| `build.gradle` | 추가 의존성 없음 (RestClient 사용) |

---

## 엣지 케이스

- **Gemini 실패 시:** DB 저장하지 않음 → 503 반환 → 프론트에서 재시도 가능
- **재요청(reRequest) 시:** 견적 내용 미변경이므로 기존 aiRiskSummary 유지
- **견적 수정 후 재요청 시:** 새 ApprovalRequest 생성 → aiRiskSummary null → 자동 재생성
