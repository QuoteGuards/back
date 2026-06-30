# QuoteGuard (Backend) — AI 작업 가이드 (CLAUDE.md)

> **이 문서는 백엔드 서버(Spring Boot) 전용 AI 컨텍스트 파일입니다.**
> 프론트엔드(React/Vite)는 별도 문서에서 다룹니다.
> Claude Code / CodeRabbit 등이 이 문서를 읽고 일관된 방식으로 **백엔드 코드**를 작성·리뷰합니다.
> **모든 설명·주석·리뷰는 한국어로 작성합니다.**

---

## 1. 프로젝트 개요

**QuoteGuard**는 Spring Boot 기반의 **B2B 견적 관리 시스템**이며, 본 문서는 그 **백엔드 서버**를 다룹니다.
견적서 생성·관리, 사용자 인증/권한, RESTful API 제공, 데이터베이스 연동을 핵심으로 합니다.

- **역할:** REST API 서버 — 프론트엔드(React/Vite, *별도 문서*)에 JSON API를 제공
- **빌드 도구:** Gradle

---

## 2. 기술 스택

| 영역 | 사용 기술 |
|------|-----------|
| Backend | Spring Boot (Java), Spring Security, Spring Data JPA / Hibernate |
| API 소비자 | React + Vite 프론트엔드 *(별도 문서에서 관리. 본 서버는 JSON API만 제공)* |
| Database | MySQL |
| Build | Gradle |
| PDF 생성 | iText7 + NotoSansKR 폰트 (한글 깨짐 방지) |
| Test | JUnit5 + Mockito *(실제 사용 여부 확인 필요)* |
| DB 마이그레이션 | 직접 작성한 DDL / Flyway *(설정 여부 확인 필요 — 4번·명령어 참고)* |
| AI 코드 리뷰 | CodeRabbit (`.coderabbit.yml`, 한국어 출력) |
| 로깅 | SLF4J + Logback |

---

## 3. 명령어

| 작업 | 명령어 |
|------|--------|
| 빌드 | `./gradlew build` |
| 실행 | `./gradlew bootRun` |
| 테스트 | `./gradlew test` |
| DB 마이그레이션 | `./gradlew flywayMigrate` ⚠️ *Flyway가 실제 설정된 경우에만 유효. 미설정 시 이 명령어는 동작하지 않으므로 DDL 직접 적용 방식을 따른다.* |

> ⚠️ **마이그레이션 방식 확인 필요:** 현재 스키마는 직접 작성한 DDL(약 15개 테이블)로 관리하는 것으로 보입니다.
> Flyway를 도입했다면 위 명령어를, 아니라면 DDL 스크립트를 SSOT로 사용하세요. **둘 중 실제 방식 하나로 이 항목을 확정**하세요.

---

## 4. 코드 스타일 가이드

Java 표준 코딩 컨벤션을 따릅니다. **네이밍 규칙은 대상별로 다릅니다.**

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 / 인터페이스 / Enum | **PascalCase** (UpperCamelCase) | `QuoteService`, `UserStatus` |
| 메서드 / 변수 / 필드 | **camelCase** (lowerCamelCase) | `createQuote()`, `quoteId` |
| 상수 (`static final`) | **UPPER_SNAKE_CASE** | `MAX_ITEM_COUNT` |
| DB 컬럼명 | **snake_case** + `@Column(name=...)` 매핑 | `created_at` → `createdAt` |

> ⚠️ **수정 사항:** Java의 변수·필드는 `snake_case`가 아니라 **`camelCase`** 입니다.
> snake_case는 DB 컬럼 등 DB 영역에서만 사용하고, 엔티티 필드명과는 `@Column`으로 매핑합니다.

기타 규칙:
- 들여쓰기 **4칸(스페이스)**, 중괄호 `{`는 같은 줄에(K&R 스타일).
- 라인 길이 **120자** 이내, 초과 시 적절히 줄바꿈.
- 주석은 **의도(why)를 설명**할 때만 작성. 코드만 봐도 아는 내용은 주석 금지.
- DTO를 컨트롤러 응답에 사용하고, 엔티티를 직접 노출하지 않는다.

---

## 5. 아키텍처

**계층화 아키텍처**를 따릅니다. 요청은 아래 순서로 흐릅니다.

```
[프론트엔드 (React/Vite · 별도 문서)]
      │  REST API (JSON)
      ▼
Controller  →  Service  →  Repository  →  Domain(Entity)
   (요청 처리)   (비즈니스 로직)  (JPA CRUD)     (핵심 객체)
                                              │
                                          [ MySQL ]
```

- **Controller** — 클라이언트 요청 수신, 검증, Service 호출. (REST API 엔드포인트 포함)
- **Service** — 비즈니스 로직, 트랜잭션 경계. 쓰기는 `@Transactional`, 읽기는 `readOnly = true`.
- **Repository** — Spring Data JPA로 DB CRUD 추상화.
- **Domain(Entity)** — 핵심 비즈니스 객체와 값 객체.

**횡단 관심사 (계층이 아닌 공통 영역):**
- **Security** — Spring Security 기반 인증/인가.
- **Config** — `application.yml` 환경별 설정.
- **Exception** — `@RestControllerAdvice` 전역 예외 처리.
- **Logging** — SLF4J + Logback.
- **Util** — 공통 유틸리티.
- **Test** — JUnit/Mockito (별도 테스트 코드, 아키텍처 계층 아님).

> 🔧 **정리 사항:** 기존 가이드는 test·logging·config·util까지 "계층"으로 나열했으나,
> 이들은 아키텍처 계층이 아니라 횡단 관심사입니다. 부트캠프 스코프에 맞게 핵심 4계층만 흐름으로 두고 나머지는 분리했습니다.

---

## 6. 비즈니스 규칙 (중요)

AI가 코드를 생성·수정할 때 반드시 지켜야 할 프로젝트 고유 규칙:

1. **계정 생성은 관리자만** — 셀프 회원가입 로직을 추가하지 말 것.
2. **`users.status` ENUM** — 계정 상태를 status ENUM으로 관리 (활성/대기/비활성 등).
3. **역할(Role) 기반 접근 제어** — 새 엔드포인트/리소스는 권한 체크를 전제로 설계.
4. **완료된 견적서도 재작성 가능** — "완료 = 불변"으로 가정하지 말 것.
5. **한글 PDF/이메일** — 견적서 PDF·메일은 한글 데이터 처리. 폰트(NotoSansKR)·인코딩 깨짐 주의.

---

## 7. 코드 품질 규칙

- **N+1 쿼리 방지** — 연관관계 조회 시 fetch join / `@EntityGraph` 활용.
- **공통 로직 추출** — 중복 메서드는 공통 유틸/서비스로 분리.
- **트랜잭션 관리** — 쓰기 `@Transactional`, 읽기 `readOnly = true`.
- **과도한 설계 지양** — 불필요한 추상화 금지. 부트캠프 스코프에 맞게 간결하게.
- **테스트** — 핵심 비즈니스 로직 위주로 단위 테스트 작성 (커버리지 욕심보다 의미 있는 테스트).

---

## 8. AI 작업 지침

- 모든 **설명·주석·리뷰는 한국어**로 작성한다.
- 코드를 제안하기 전 **6번 비즈니스 규칙**과 충돌하지 않는지 확인한다.
- 스키마·기능·설정(Flyway 등)이 불확실하면 **추측해서 단정하지 말고** 어떤 가정인지 명시한다.
- 기존 패키지 구조·네이밍 컨벤션을 따른다.

---

## 9. 협업 / 버전 관리

- 모든 코드는 **Git**으로 버전 관리, 커밋 메시지에 변경 의도를 명확히 기록.
- 커밋 컨벤션 사용 (예: `feat:`, `fix:`, `refactor:`, `docs:` …).
- PR은 **CodeRabbit**으로 AI 리뷰를 받으며, **리뷰 출력은 한국어**.
- 코드 변경 시 이 문서도 함께 최신 상태로 유지한다.

---

> _`⚠️ 확인 필요` 표시 항목(Flyway, 테스트 스택)은 실제 프로젝트 설정에 맞춰 확정하세요._