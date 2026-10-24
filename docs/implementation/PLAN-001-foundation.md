# PLAN-001: M0 기반 부트스트랩 상세 구현 계획

> 보관 문서 (2026-10-24): 작성 당시의 설계·제안·구현 및 검증 기록입니다. 본문의 상태·체크리스트·작업 순서·승인 조건은 당시 기록이며 현재 개발 지침이 아닙니다. 미구현 제안도 포함하므로 보관 전환은 구현 완료를 뜻하지 않습니다. 현재 할 일은 [TODO](../todo.md), 동작과 요구사항은 [제품 사양](../product-spec.md) 및 기능별 문서를 확인합니다.

> 상태: M0 구현 완료
> 역할: 구현 로드맵 M0의 저장소, 인프라, 백엔드·프론트엔드 부트스트랩 계획
> 상위 문서: [구현 로드맵](../implementation-roadmap.md)
> 후속 문서: [M0 계약·CI·검증 계획](PLAN-001-foundation-delivery.md)
> 관련 문서: [아키텍처](../architecture.md), [운영](../operations.md), [계약](../contracts.md), [ADR-004](../decisions/ADR-004-mybatis.md), [ADR-005](../decisions/ADR-005-rabbitmq-outbox.md), [ADR-006](../decisions/ADR-006-session-auth.md), [ADR-014](../decisions/ADR-014-generated-docs-draft.md)


## 0. 확정된 실행 결정

2026-10-20에 다음 항목을 확인하고 구현에 반영했습니다.

| 항목 | 확정 내용 |
|---|---|
| 구현 범위 | M0 기반 전체 + E안 디자인 시스템 + SCR-003·004·009 정적 미리보기 |
| 계약 생성 | M0는 `build/contracts/` 미리보기 모드. `docs/generated/` 설계 초안 보존 ([ADR-015](../decisions/ADR-015-contract-preview-mode.md)) |
| 도구 기준 | Java 21.0.12.1, Node 22.23.3, pnpm 10.34.7, Gradle 8.14.3을 `.tools/`에 설치 |
| 디자인 문서 | `design/design-system.md`를 `docs/design-system.md`로 승격하고 `DESIGN`으로 등록 ([ADR-016](../decisions/ADR-016-neo-brutal-design-system.md)) |
| 저장소 | Git 저장소를 초기화하고 검증 후 초기 커밋 |
| MinIO | 공식 이미지 미제공으로 로컬은 `bitnamilegacy/minio:2025.7.23-debian-12-r5` 고정 태그 사용 |
| Python | CI는 3.12, 로컬 검증은 3.14. 계약 스크립트는 두 버전에서 동작 |

## 1. 목적

M0의 목적은 기능을 만드는 것이 아니라, 이후 M1~M8을 같은 방식으로 반복할 수 있는 개발 기반을 만드는 것입니다.

- 백엔드, 프론트엔드, 로컬 인프라를 한 저장소에서 실행합니다.
- 테스트, CI, 문서 검사, 계약 생성 절차를 먼저 고정합니다.
- 테넌트 격리와 보안 설정을 나중에 끼워 넣지 않도록 기본 구조에 자리를 만듭니다.
- M1 이후의 마일스톤이 재작업 없이 시작될 수 있게 합니다.

M0가 끝나면 인증이나 도메인 API는 없지만, `docker compose`로 인프라를 띄우고, 백엔드와 프론트엔드를 빌드·테스트하며, 계약 문서를 생성하는 절차가 동작해야 합니다.

## 2. 범위

### 2.1 포함

- monorepo 디렉터리와 빌드 파일
- Gradle Kotlin DSL 기반 Spring Boot 애플리케이션 골격
- pnpm 기반 React·TypeScript·Vite 애플리케이션 골격
- PostgreSQL 16 + pgvector, RabbitMQ, MinIO, Mailpit 로컬 구성
- Flyway 확장 마이그레이션과 MyBatis 설정
- Testcontainers 기반 스모크 테스트
- Spring Boot Actuator liveness/readiness
- `request_id` 기반 구조화 로그
- OpenAPI, 데이터 사전, 이벤트 계약 생성기 골격
- `scripts/docs_check.py` 확장
- GitHub Actions CI
- M0 검증 절차와 문서 갱신

### 2.2 제외

- 회원가입, 로그인, 세션 발급, CSRF 실제 처리
- `app_user`, `space`, `room`, `shelf`, `box`, `item`, `inventory` 등 도메인 테이블
- 실제 API 비즈니스 로직
- 이미지 업로드와 후처리
- AI, 임베딩, 벡터 검색
- 프로덕션 배포와 운영 인프라 프로비저닝

M1에서 세션 테이블과 인증 API를 추가합니다. M3에서 첫 도메인 테이블을 추가합니다.

## 3. 전제와 버전

| 항목 | 기준 |
|---|---|
| Java | 21.0.12.1 (Temurin) |
| Kotlin | 2.0.21 (detekt 1.23.8 호환) |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3, Kotlin DSL, wrapper |
| Node.js | 22.23.3 |
| pnpm | 10.34.7 |
| React | 19.3.1 |
| TypeScript | 5.9.3 |
| Vite | 7.3.7 |
| PostgreSQL | 16 + pgvector 0.8.7 |
| RabbitMQ | 3.13.7 관리 플러그인 포함 |
| MinIO | `bitnamilegacy/minio:2025.7.23-debian-12-r5` (로컬 전용) |
| Mailpit | v1.31.6 |
| Python | CI 3.12, 로컬 3.14, 계약 스크립트용 |

- 정확한 버전은 `gradle/libs.versions.toml`, `package.json`, `pnpm-lock.yaml`, Docker 이미지 태그에 고정합니다.
- `latest` 태그는 로컬에서도 사용하지 않습니다.
- 버전 업그레이드는 별도 PR로 분리합니다.

## 4. 최종 구조

```text
settings.gradle.kts              루트 Gradle 멀티 프로젝트
gradle/libs.versions.toml        의존성·플러그인 버전 카탈로그
gradle/wrapper/                  Gradle wrapper
backend/
  build.gradle.kts
  src/main/kotlin/com/agolock/
    common/ config, error, id, logging, mybatis
    system/ InfoController
  src/main/resources/
    application*.yml
    logback-spring.xml
    db/migration/V1__extensions.sql
    asyncapi/events.yaml
  src/test/kotlin/com/agolock/
    common/ error, id, logging
    contract/ OpenApiExportTest
    smoke/ ApplicationSmokeTest, DatabaseMigrationTest, RabbitMqSmokeTest
    support/ TestcontainersConfiguration
frontend/
  package.json
  vite.config.ts
  playwright.config.ts
  src/
    app/ AppShell, router, providers, error boundary
    features/ home, design-preview, placeholders, errors
    shared/ api, lib, navigation, theme, ui
    styles/globals.css
    test/
  e2e/smoke.spec.ts
infra/docker-compose.yml
infra/minio/init.sh
scripts/
  install-tools.sh, env.sh
  docs_check.py, docs_impact.py
  contract_validation.py, normalize_openapi.py
  export_events.py, generate_data_dictionary.py
  docs_generate.sh
build/contracts/                 계약 미리보기 (커밋하지 않음)
.github/workflows/ docs.yml, ci.yml
```

- M0는 단일 Gradle 프로젝트와 단일 프론트엔드 패키지로 시작합니다.
- 패키지 경계는 `com.agolock.<module>`로 나누고, 빌드 모듈 분리는 실제 필요가 생길 때 검토합니다.
- `docs/generated/`는 M0에서 생성기 골격만 만들고, 실제 계약은 각 마일스톤에서 채웁니다.

## 5. 작업 분해

### Step 0. 착수 전 확인

- [x] ADR-001~014 승인 상태 확인
- [x] M0 범위에서 인증·도메인 API를 제외한다는 합의
- [x] JDK 21, Node 22, pnpm 10, Docker 설치 확인
- [x] 로컬 포트 충돌 확인: 5432, 5672, 15672, 9000, 9001, 1025, 8025, 8080, 5173
- [x] 저장소 루트에 `.editorconfig`, `.gitignore`, `.gitattributes` 추가

산출물:

- 착수 체크리스트
- 로컬 포트와 환경 변수 표

### Step 1. 저장소 골격

작업:

- [x] `backend/`, `frontend/`, `infra/`, `scripts/`, `.github/workflows/` 생성
- [x] `backend/settings.gradle.kts`와 `build.gradle.kts` 생성
- [x] `gradle/libs.versions.toml` 생성
- [x] Gradle wrapper 생성
- [x] 루트 `pnpm-workspace.yaml`에 `frontend` 등록
- [x] 루트 `package.json`에 `lint`, `test`, `build`, `dev`, `generate:api` 위임 스크립트 추가
- [x] 루트 `README.md`에 로컬 실행 명령 추가
- [x] `.gitignore`에 Gradle, Node, IDE, `.env.local`, Docker 볼륨 제외

검증:

- `./gradlew help`가 실패 없이 실행됩니다.
- `./gradlew tasks`에서 커스텀 task가 보입니다.

### Step 2. 로컬 인프라

`infra/docker-compose.yml`에 다음 서비스를 둡니다.

| 서비스 | 이미지 | 포트 | 용도 |
|---|---|---|---|
| postgres | `pgvector/pgvector:pg16` | 5432 | PostgreSQL + pgvector |
| rabbitmq | `rabbitmq:3.13-management` | 5672, 15672 | Outbox·MQ |
| minio | 고정 태그 | 9000, 9001 | S3 호환 스토리지 |
| minio-init | `minio/mc` 고정 태그 | - | 버킷 생성 |
| mailpit | 고정 태그 | 1025, 8025 | 초대·재설정 메일 |

작업:

- [x] PostgreSQL 데이터베이스 `agolock`, 사용자 `agolock`, 비밀번호 로컬 값 설정
- [x] PostgreSQL healthcheck `pg_isready`
- [x] RabbitMQ healthcheck `rabbitmq-diagnostics ping`
- [x] MinIO healthcheck와 `agolock-local` 버킷 생성
- [x] 버킷은 비공개로 생성
- [x] Mailpit SMTP 1025, 웹 8025 노출
- [x] 데이터 볼륨과 네트워크 정의
- [x] `.env.local.example` 추가
- [x] `infra/minio/init.sh`에 버킷 생성과 익명 접근 차단 명령 작성

검증:

- `docker compose -f infra/docker-compose.yml up -d` 후 모든 서비스가 healthy입니다.
- `psql`로 `CREATE EXTENSION vector`가 가능합니다.
- RabbitMQ 관리 콘솔에 접속됩니다.
- MinIO 콘솔에서 버킷이 비공개로 보입니다.
- Mailpit 웹이 열립니다.

### Step 3. 백엔드 부트스트랩

의존성:

- Spring Web, Validation, Actuator
- Spring Security는 M0에서 모든 요청을 `permitAll`로 두고 CSRF를 비활성화합니다. M1에서 세션·CSRF 규칙을 활성화합니다.
- Spring Session JDBC는 M1에서 스키마와 함께 활성화합니다.
- Spring AMQP
- MyBatis Spring Boot Starter
- Flyway Core, Flyway PostgreSQL
- PostgreSQL Driver
- Jackson Kotlin
- Logstash Logback Encoder
- Testcontainers PostgreSQL, RabbitMQ
- Spring Boot Test
- ktlint, detekt 플러그인

작업:

- [x] `AgolockApplication.kt` 생성
- [x] `application.yml`에 datasource, Flyway, MyBatis, Actuator, 로깅 설정
- [x] `application-local.yml`에 로컬 접속 정보와 MinIO·Mailpit 설정
- [x] `db/migration/V1__extensions.sql` 작성
  - `pg_trgm`
  - `vector`
  - `citext`
- [x] `SystemMapper`와 `SELECT 1` 스모크 매퍼 작성
- [x] `UuidV7` 생성기와 테스트 작성
  - 시간 정렬 가능한 UUIDv7
  - 테스트용 고정 Clock 주입
- [x] `ClockProvider`와 `@ConfigurationProperties` 검증 작성
  - 필수 환경 변수 누락 시 기동 실패
- [x] `RequestIdFilter` 작성
  - `X-Request-Id`가 있으면 사용, 없으면 UUID 생성
  - MDC에 `request_id` 저장
  - 응답 헤더에 반환
- [x] JSON 로그 패턴 설정
- [x] 로그 마스킹 기본 설정
  - 세션 ID, 비밀번호, 초대 토큰, presigned URL, AI 원본 응답을 남기지 않음
- [x] ktlint와 detekt 설정
- [x] Actuator health group 설정
  - liveness: `livenessState`
  - readiness: `db`, `rabbit`
- [x] 공통 `ProblemDetail` 처리 골격 작성
  - M0에서는 매핑만, 도메인 오류 코드는 M1 이후 추가
- [x] `TestcontainersConfiguration.kt` 작성
  - PostgreSQL은 `pgvector/pgvector:pg16`
  - RabbitMQ 컨테이너
- [x] `ApplicationSmokeTest` 작성
  - Spring 컨텍스트 로드
  - `/actuator/health/liveness` 200
- [x] `DatabaseMigrationTest` 작성
  - Flyway 적용
  - `vector`, `pg_trgm`, `citext` 확장 확인
  - MyBatis `SELECT 1`
- [x] `RabbitMqSmokeTest` 작성
  - 연결과 management 포트 확인

검증:

- `./gradlew test` 통과
- `./gradlew ktlintCheck detekt` 통과
- `./gradlew bootRun --args='--spring.profiles.active=local'` 기동
- `curl localhost:8080/actuator/health`가 `UP`
- 로그에 `request_id` 포함
- 필수 환경 변수를 제거했을 때 기동이 명확한 오류로 실패

### Step 4. 프론트엔드 부트스트랩

작업:

- [x] Vite + React + TypeScript 프로젝트 생성
- [x] `packageManager` 필드로 pnpm 버전 고정
- [x] React Router, TanStack Query, React Hook Form, Zod, Tailwind, shadcn/ui 설정
- [x] `openapi-typescript` 추가와 `pnpm generate:api` 스크립트 작성
- [x] 타입 파일은 Step 5의 OpenAPI 생성 이후 `pnpm generate:api`로 생성
- [x] ESLint, Prettier, Vitest, React Testing Library, Playwright 설정
- [x] `shared/api/client.ts` 작성
  - `credentials: include`
  - `X-XSRF-TOKEN` 헤더 자리
  - `application/problem+json` 파서
  - 401·403·404·409·422·429·503 공통 처리
- [x] `app/queryClient.ts` 작성
- [x] `app/router.tsx`에 최소 라우트와 오류 경계 구성
- [x] M0 홈 화면은 API 호출 없이 앱 이름과 환경 표시만 제공
- [x] Playwright smoke 테스트로 앱 로드 확인

검증:

- `pnpm lint`
- `pnpm test`
- `pnpm build`
- `pnpm exec playwright test`
- `pnpm --filter frontend dev`에서 `localhost:5173` 접속
- `pnpm generate:api`는 Step 5 이후에 실행

## 6. M0 실행 결과

| 검증 | 결과 |
|---|---|
| `docker compose -f infra/docker-compose.yml up -d` | PostgreSQL, RabbitMQ, MinIO, Mailpit 모두 healthy |
| `./gradlew :backend:test` | 20개 테스트 통과 |
| `./gradlew :backend:ktlintCheck :backend:detekt` | 통과 |
| `pnpm lint`, `pnpm test`, `pnpm build` | 통과 |
| `pnpm test:e2e` | Playwright 스모크 통과 |
| `/actuator/health/liveness`, readiness | `UP` |
| `request_id` 로그·응답 헤더 | 구현 및 테스트 완료 |
| `bash scripts/docs_generate.sh all` | OpenAPI 1 path, 데이터 사전 1 table, 이벤트 0 channel 미리보기 생성 |
| `python3 scripts/docs_check.py` | 통과 |
| CI workflow | `docs`, `backend`, `frontend`, `contract` job 작성 완료. GitHub 원격 실행은 push 후 확인 |

구현 중 계획과 달라진 점은 다음과 같습니다.

- Gradle은 루트 wrapper + `backend` 하위 프로젝트로 구성했습니다. 명령은 계획과 같이 루트에서 `./gradlew`를 사용합니다.
- 공식 MinIO 이미지가 Docker Hub에서 내려가 로컬 전용 Bitnami legacy 고정 태그로 대체했습니다.
- M0 홈은 앱 이름과 환경 표시에 더해 디자인 시스템 검증용 `/design-preview`를 제공합니다.
- 계약 생성 결과는 [ADR-015](../decisions/ADR-015-contract-preview-mode.md)에 따라 `docs/generated/`가 아니라 `build/contracts/`에 기록했습니다.
- M1의 Spring Session JDBC 스키마는 계획대로 M0에 넣지 않았습니다.

## 7. 다음 문서


- [M0 계약·CI·검증 계획](PLAN-001-foundation-delivery.md)
- [구현 로드맵](../implementation-roadmap.md)
- [운영과 검증](../operations.md)
- [API와 이벤트 계약](../contracts.md)
