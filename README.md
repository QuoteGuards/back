# QuoteGuard Backend

## 프로젝트 소개

**QuoteGuard**는 B2B 영업 견적을 작성·검토·승인·발송까지 한 흐름으로 관리하는 시스템입니다.


- 견적 금액·VAT·이익률 산정, 할인 정책 검증, 승인 판정 등 **비즈니스 최종 판정은 백엔드**가 수행합니다.
- 역할 기반 접근 제어(`SUPER_ADMIN` / `SALES_MANAGER` / `SALES_STAFF`)
- 계정은 관리자가 생성하며, 셀프 회원가입 API는 제공하지 않습니다.

> 비즈니스 규칙 상세: 노션 링크

---

## 기술 스택

- **Java 21**, **Spring Boot 4.1.0**
- **JPA/Hibernate**, **QueryDSL 5.1** (Jakarta)
- **MySQL 8**, **JWT** (Access + Refresh, jjwt)
- Spring Security, Spring Validation, Spring Mail
- Gradle (빌드 도구)
- iText7 (PDF), AWS S3 (선택적 파일 저장)
- Gemini / Groq (AI 리스크 요약, 선택)
- spring-dotenv (`.env` 로컬 환경 변수)

---

## 팀원 역할 분담

| 이름 | 역할 | 담당 |
|------|------|------|
| 홍창희 | 팀장 | 계정 관리, 인증/인가, 사용자 통계, CI/CD |
| 박재석 | 팀원 | 제품 관리 및 탐색, 할인 정책 관리, 통계 대시보드 |
| 박삼령 | 팀원 | 견적 계산 및 작성, 내부 분석, 고객 관리, 임시 저장, 교육(LMS) |
| 신현섭 | 팀원 | 승인/반려 처리, 재요청, SLA 알림 및 견적 리마인더, AI 리스크 요약  |
| 박준호 | 팀원 | 견적서 미리보기, PDF/엑셀 다운로드, 이메일 발송, 알림(SSE) |
| 장채은 | 팀원 | 상담 메모 요약, 제안 문구 생성 |

---

## 주요 기능

### 계정·운영·관리
- JWT 로그인/갱신, 초기 비밀번호 설정·비밀번호 재설정
- 관리자 사용자 CRUD, 부서별 통계
- 관리자 대시보드, 인앱 알림

### 견적
- 견적 CRUD, 임시저장·작성완료, 복사·만료 재작성
- 품목별 할인·VAT(10%)·이익률 자동 산정
- 할인 정책 스냅샷 저장, 할인 사유 검증
- 내부 견적 분석 API (원가·이익·정책 대비)

### 승인
- 작성완료 시 승인 필요 3조건 판정 (`ApprovalCheckService`)
  - 할인 초과 / 저이익 / 고액
- 승인 요청·승인·반려·재요청·요청 철회
- SLA 초과 승인 건 인앱 알림 (스케줄러)
- AI 리스크 요약 (Gemini, 한도 초과 시 Groq fallback)

### 마스터·영업 지원
- 거래처, 제품·카테고리, 할인 정책, 제품 즐겨찾기
- 견적 PDF 생성·이메일 발송·발송 이력
- 견적 만료 알림

### 교육(LMS)
- 교육 영상 이수 현황, 가이드 확인
- 관리자 교육 콘텐츠·영상 관리
- 승인 권한 부여 전 필수 교육 이수 게이트

### AI 보조 지원
- AI 리스크 요약
- AI 상담 메모 요약
- 제안 문구 생성

---

## ERD

| 자료 | 위치 |
|------|------|
| **DB 초기 스크립트 (DDL)** | [`sql/QuoteGuard.sql`](./sql/QuoteGuard.sql) |
| **핵심 테이블 요약** | [SSOT §8 연관 테이블](./docs/QUOTE_BUSINESS_RULES_SSOT.md#8-연관-테이블-erd-핵심) |
| **ERD 다이어그램 이미지** | _(팀 노션/위키 링크 또는 `docs/erd.png` 추가)_ |

---

## API 명세

| 방식 | 링크 |
|------|------|
| Notion API 문서 | _(팀 노션 링크)_ |

---

## 실행 방법 (프론트 / 백엔드 공통)

### 1. 사전 요구 사항

| 항목 | 버전 |
|------|------|
| JDK | 21 |
| MySQL | 8+ |
| Node.js (프론트) | 18+ |
| Git | 최신 |

### 2. 저장소 클론

```
# 백엔드
git clone https://github.com/QuoteGuards/back.git
cd back

# 프론트엔드 (별도 터미널)
git clone https://github.com/QuoteGuards/front.git
cd front
```

### 3. DB 준비
MySQL 접속 후 QuoteGuard.sql 실행

### 4. 백엔드 환경 설정 
.env 파일 생성 후 아래 내용 복붙하여 입력 후 저장

# QuoteGuard 환경변수 템플릿

── Database ──
DB_SCHEMA_NAME=quoteguard
DB_USERNAME=
DB_PASSWORD=

── JWT ── (512bit base64 문자열)
JWT_SECRET=

── Mail (SMTP) ──
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
MAIL_FROM_NAME=QuoteGuard

── File Storage (local | s3) ──
STORAGE_TYPE=local
STORAGE_PUBLIC_BASE_URL=http://localhost:8080
STORAGE_LOCAL_DIR=./uploads
STORAGE_TYPE=s3
S3_BUCKET=
S3_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
S3_PUBLIC_URL=

── Gemini (AI) ──
GEMINI_API_KEY=

── Frontend ──
APP_FRONTEND_URL=http://localhost:5173
-프론트 빌드 시 API base URL (비워두면 상대경로 /api 사용, nginx가 BACKEND_HOST로 프록시)
VITE_API_BASE_URL=

### 5. 백엔드 실행(API 기본 URL: http://localhost:8080)

# Windows
gradlew.bat bootRun
# macOS / Linux
./gradlew bootRun


### 6. 프론트엔드 환경 설정/실행(프론트 기본 URL: http://localhost:5173)
cd front
npm install
npm run dev

---

## 패키지 구조
src/main/java/com/project/back/
├── domain/
│   ├── auth/          # 인증
│   ├── quote/         # 견적·금액 계산
│   ├── approval/      # 승인
│   ├── product/       # 제품
│   ├── category/      # 카테고리
│   ├── discount/      # 할인 정책
│   ├── customer/      # 거래처
│   ├── training/      # 교육
│   ├── email/         # 이메일
│   ├── document/      # PDF
│   ├── user/          # 사용자
│   └── dashboard/     # 대시보드
├── notification/      # 인앱 알림
├── ai/                # AI 리스크 요약
└── global/            # 보안·예외·공통

---

## 협업
비즈니스 규칙 : 노션 링크
코드 리뷰: CodeRabbit
프론트 저장소: https://github.com/QuoteGuards/front

