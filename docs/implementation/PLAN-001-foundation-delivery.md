# PLAN-001 후속: M0 계약·CI·검증 계획

> 상태: M0 구현 완료
> 역할: M0의 계약 생성기, CI, 데이터 기준선, 테스트, 완료 조건
> 상위 문서: [M0 기반 부트스트랩](PLAN-001-foundation.md)
> 관련 문서: [구현 로드맵](../implementation-roadmap.md), [운영](../operations.md), [계약](../contracts.md), [ADR-014](../decisions/ADR-014-generated-docs-draft.md), [ADR-015](../decisions/ADR-015-contract-preview-mode.md)


## 0. M0 확정 사항

- 생성 결과는 `build/contracts/`에만 기록하고 `docs/generated/` 설계 초안을 보존합니다 ([ADR-015](../decisions/ADR-015-contract-preview-mode.md)).
- `scripts/docs_generate.sh`는 `openapi`, `schema`, `events`, `all`, `check`를 지원합니다.
- `pnpm generate:api`는 `docs/generated/openapi.yaml`에서 타입을 생성하고 CI에서 diff를 확인합니다.
- CI는 `docs`, `backend`, `frontend`, `contract` 네 job으로 구성했습니다.
- 데이터 사전 생성은 `psycopg[binary]`를 사용하며 PostgreSQL이 실행 중이어야 합니다.
- M1 호환성 검사, 의존성 잠금, `pnpm audit`, 비밀 스캔은 M0 완료 조건이 아닙니다.

## 1. 목적과 범위

이 문서는 M0의 후반부 작업을 정의합니다.

- OpenAPI, 데이터 사전, 이벤트 계약 생성기 골격
- 문서 검사 확장
- GitHub Actions CI
- 데이터베이스 기준선
- 환경 변수와 테스트 계획
- M0 완료 조건과 위험

저장소 구조, 로컬 인프라, 백엔드·프론트엔드 부트스트랩은 [M0 기반 부트스트랩](PLAN-001-foundation.md)을 따릅니다.

## 2. Step 5. 계약 생성 파이프라인

로컬 Python 의존성은 `python3 -m pip install pyyaml 'psycopg[binary]'`로 설치합니다.

### 2.1 OpenAPI

작업:

- [x] springdoc-openapi 의존성과 Gradle export task 추가
- [x] `scripts/normalize_openapi.py` 작성
  - JSON을 YAML로 변환
  - 상단에 생성 미리보기 헤더 유지
  - operationId 중복 검사
  - path parameter 누락 검사
- [x] `docs_generate.sh openapi`가 `build/contracts/openapi.yaml` 미리보기를 갱신
- [x] 계약 미리보기 테스트는 `contract` 프로필을 사용해 DB·Rabbit 없이 기동합니다.
- [x] M0에는 `/api/v1/info` 컨트롤러만 있으므로 1개 경로의 최소 문서를 생성

검증:

- `bash scripts/docs_generate.sh openapi` 실행
- `build/contracts/openapi.yaml` 파싱
- operationId 중복 없음
- path parameter 누락 없음
- 생성 미리보기 헤더 유지

### 2.2 데이터 사전

작업:

- [x] `scripts/generate_data_dictionary.py` 작성
  - `DATABASE_URL` 또는 로컬 기본값 사용
  - `information_schema`와 `pg_catalog` 조회
  - 테이블, 컬럼, 타입, nullability, 기본값, 제약, 인덱스, enum 출력
  - 문서 머리말에 생성 미리보기 표시
- [x] `docs_generate.sh schema` 추가
- [x] M0에서는 확장과 Flyway 이력만 출력되는 것을 확인
- [ ] M1부터 실제 테이블 출력

검증:

- `bash scripts/docs_generate.sh schema` 실행
- PostgreSQL 확장과 Flyway 이력이 출력됨
- Markdown 링크와 표가 깨지지 않음

### 2.3 이벤트 계약

작업:

- [x] `backend/src/main/resources/asyncapi/events.yaml`을 이벤트 계약 원본으로 둡니다.
- [x] `scripts/export_events.py` 작성
  - AsyncAPI YAML 파싱
  - 채널·메시지·스키마 `$ref` 검사
  - `build/contracts/events.yaml`로 복사하며 생성 미리보기 헤더 유지
- [x] `docs_generate.sh events` 추가

검증:

- `bash scripts/docs_generate.sh events` 실행
- AsyncAPI 채널과 메시지 `$ref` 유효
- 미리보기 파일의 헤더 유지

### 2.4 문서 검사 확장

`scripts/docs_check.py`에 다음 검사를 추가합니다.

- [x] generated 문서의 설계 초안 헤더와 미리보기의 생성 헤더 존재
- [x] OpenAPI operationId 중복
- [x] OpenAPI path parameter 존재
- [x] AsyncAPI 채널·메시지 `$ref` 유효성
- [x] `last_verified_commit`이 `pending` 또는 40자 SHA인지 확인
- [x] 실패 시 CI가 중단되도록 exit code 유지

검증:

- `bash scripts/docs_generate.sh all`
- `python3 scripts/docs_check.py`
- 고의로 깨진 `$ref`를 넣었을 때 검사가 실패하는지 확인

### 2.5 프론트엔드 API 타입 생성

- [x] `openapi-typescript`를 프론트엔드 dev 의존성에 추가
- [x] `pnpm generate:api`가 `docs/generated/openapi.yaml`에서 `frontend/src/shared/api/schema.d.ts`를 생성
- [x] 생성된 타입을 `client.ts`와 기능 코드에서 사용
- [x] API 변경 PR은 `pnpm generate:api` 결과를 함께 커밋
- [x] CI frontend job에서 타입 재생성 후 diff가 없는지 확인

검증:

- `pnpm generate:api` 실행
- `pnpm lint`, `pnpm build` 통과
- API 응답 필드 변경 시 타입 diff가 발생

### 2.6 API 호환성 검사

- [ ] M1부터 contract job에서 이전 OpenAPI와 현재 OpenAPI를 비교
- [ ] breaking change가 있으면 PR을 실패시킴
- [ ] 허용 예외는 ADR 또는 PR 설명에 기록

## 3. Step 6. CI

`.github/workflows/ci.yml`:

- `docs` job
  - Python 3.12
  - `pyyaml` 설치
  - `python3 scripts/docs_check.py`
- `backend` job
  - JDK 21
  - Gradle cache
  - `./gradlew ktlintCheck detekt test`
  - Testcontainers용 Docker 사용
- `frontend` job
  - Node 22
  - pnpm 10
  - pnpm cache
  - `pnpm generate:api`
  - `pnpm lint`, `pnpm test`, `pnpm build`
  - `pnpm exec playwright install --with-deps`
  - `pnpm exec playwright test`
- `contract` job은 M1부터 활성화
  - Python 3.12, `pyyaml`, `psycopg[binary]` 설치
  - OpenAPI 타입 재생성 diff 확인
  - M0에서는 생성기 명령이 존재하는지만 확인
- 보안·의존성 스캔은 M8에서 필수 gate로 승격하고, M0에서는 Dependabot과 `pnpm audit`을 먼저 켭니다.
- 기존 `docs.yml`은 유지하고 `ci.yml`과 역할을 분리합니다.

작업:

- [x] `.github/dependabot.yml`에 Gradle, pnpm, GitHub Actions 업데이트 설정
- [ ] `pnpm audit --prod`를 CI 또는 주간 job에 추가
- [ ] Gradle 의존성 잠금 또는 검증 정책을 정합니다.
- [ ] 비밀 스캔은 M8에서 gitleaks 또는 GitHub secret scanning으로 필수화합니다.

검증:

- PR에서 세 job이 모두 통과
- job 실패 시 로그에 재현 명령이 남음
- 캐시가 두 번째 실행에서 동작

## 4. Step 7. 검증과 문서 갱신

- [x] `docker compose config` 검증
- [x] `docker compose -f infra/docker-compose.yml up -d` 후 healthy 확인
- [x] `bash scripts/docs_generate.sh all`
- [x] `pnpm generate:api`
- [x] `pnpm lint && pnpm test && pnpm build`
- [x] `./gradlew ktlintCheck detekt test`
- [x] `python3 scripts/docs_check.py`
- [x] `docs/index.yaml`의 `last_updated` 갱신
- [x] `README.md`에 로컬 실행과 검증 명령 추가
- [x] M0 결과를 구현 로드맵의 M0 완료 조건과 대조

## 5. 데이터베이스 기준선

M0의 Flyway는 확장만 준비합니다.

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;
```

- 관리형 PostgreSQL에서 확장 생성 권한이 없으면 배포 전에 DBA가 확장을 준비합니다.
- Spring Session JDBC 테이블은 M1에서 라이브러리 버전에 맞는 DDL을 Flyway 마이그레이션으로 추가합니다.
- 도메인 테이블은 M1부터 마일스톤별로 추가합니다.
- M0에서는 데이터 사전 생성기가 빈 스키마에서도 동작하는지 확인합니다.

## 6. 설정과 환경 변수

| 변수 | 로컬 예 | 용도 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | 프로필 |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/agolock` | DB |
| `DATABASE_USERNAME` | `agolock` | DB |
| `DATABASE_PASSWORD` | 로컬 전용 값 | DB |
| `RABBITMQ_HOST` | `localhost` | MQ |
| `RABBITMQ_PORT` | `5672` | MQ |
| `S3_ENDPOINT` | `http://localhost:9000` | MinIO |
| `S3_BUCKET` | `agolock-local` | 이미지 버킷 |
| `S3_ACCESS_KEY` | 로컬 전용 값 | MinIO |
| `S3_SECRET_KEY` | 로컬 전용 값 | MinIO |
| `MAIL_HOST` | `localhost` | Mailpit |
| `MAIL_PORT` | `1025` | Mailpit |
| `AUTH_SIGNUP_INVITE_REQUIRED` | `true` | M1에서 사용 |
| `SENTRY_DSN` | 비어 있음 | M0에서는 선택 |

- 비밀 값은 `.env.local`에만 두고 커밋하지 않습니다.
- `.env.local.example`에는 값 대신 설명을 넣습니다.

## 7. 테스트 계획

### 7.1 백엔드

| 테스트 | 확인 내용 |
|---|---|
| `ApplicationSmokeTest` | Spring 컨텍스트, Actuator liveness |
| `DatabaseMigrationTest` | Flyway, pgvector, pg_trgm, citext, MyBatis `SELECT 1` |
| `RabbitMqSmokeTest` | RabbitMQ 연결 |
| `RequestIdFilterTest` | 요청 ID 생성·전달·응답 헤더 |
| `UuidV7Test` | 시간 정렬, 형식, 고정 Clock |
| `ConfigValidationTest` | 필수 환경 변수 누락 시 기동 실패 |
| `LogMaskingTest` | 민감 값이 로그에 남지 않음 |
| `ProblemDetailTest` | 기본 오류 응답 형식 |

### 7.2 프론트엔드

| 테스트 | 확인 내용 |
|---|---|
| `App.test.tsx` | 앱 렌더링, 오류 경계 |
| `client.test.ts` | problem+json 파싱, 공통 오류 매핑 |
| `smoke.spec.ts` | Playwright 앱 로드 |

### 7.3 인프라

- Compose 서비스 healthcheck
- MinIO 버킷 비공개 확인
- Mailpit SMTP 연결
- PostgreSQL 확장 확인

### 7.4 계약

- OpenAPI operationId 중복 없음
- path parameter 누락 없음
- AsyncAPI `$ref` 유효
- generated 헤더 유지
- `docs_check.py` 통과

## 8. 완료 조건 체크리스트

- [x] `docker compose -f infra/docker-compose.yml up -d` 성공
- [x] 모든 로컬 서비스 healthy
- [x] `./gradlew test` 통과
- [x] `pnpm lint`, `pnpm test`, `pnpm build` 통과
- [x] `pnpm exec playwright test` 통과
- [x] `/actuator/health/liveness`와 readiness 200
- [x] `request_id`가 로그와 응답에 포함
- [x] `bash scripts/docs_generate.sh all` 실행 가능
- [x] `python3 scripts/docs_check.py` 통과
- [ ] CI의 docs, backend, frontend job 통과 (workflow 작성 완료, GitHub 원격 실행 대기)
- [x] M0 비목표가 코드에 들어오지 않음
- [x] 구현 로드맵 M0 완료 조건과 일치

실행 결과의 상세는 [M0 기반 부트스트랩 6번 항목](PLAN-001-foundation.md)을 따릅니다.

## 9. 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| pgvector 확장 권한 부족 | 마이그레이션 실패 | 로컬은 superuser, 운영은 사전 프로비저닝 |
| Testcontainers 이미지 불일치 | 테스트 실패 | 이미지 태그 고정 |
| Spring Session DDL 지연 | M1 착수 지연 | M0에서 라이브러리 버전과 DDL을 미리 확인 |
| pnpm/Gradle 버전 드리프트 | CI 재현성 저하 | wrapper와 lockfile 고정 |
| 계약 생성기 과설계 | M0 일정 지연 | M0는 골격과 검사만, 완성은 마일스톤별 |
| MinIO 초기화 실패 | 이미지 기능 지연 | `minio-init`을 별도 서비스로 두고 healthcheck |
| CI 시간 증가 | 피드백 지연 | 캐시, job 분리, M0 테스트 최소화 |
| generated 문서 수동 수정 재발 | 계약 불일치 | 생성 명령과 docs_check gate |

## 10. 예상 순서와 규모

| 순서 | 작업 | 예상 |
|---|---|---|
| 1 | 저장소 골격 | 0.5일 |
| 2 | 로컬 인프라 | 0.5일 |
| 3 | 백엔드 부트스트랩 | 2일 |
| 4 | 프론트엔드 부트스트랩 | 1.5일 |
| 5 | 계약 생성기 골격 | 1.5일 |
| 6 | CI와 문서 검사 | 0.5일 |
| 7 | 검증과 문서 갱신 | 0.5일 |

- 1인 기준 6~7일을 예상합니다.
- 프론트엔드와 백엔드를 병렬로 진행하면 5일 전후로 줄일 수 있습니다.
- 계약 생성기에서 예상보다 시간이 걸리면 M0 완료 조건을 "명령과 검사 골격"으로 유지하고, 실제 생성은 M1에서 완성합니다.

## 11. M0 계획 검토

### 11.1 검토 항목

- [x] M0가 인증·도메인 기능을 포함하지 않는가
- [x] 각 단계가 독립적으로 검증 가능한가
- [x] 테스트가 실제 인프라를 사용하는가
- [x] 계약 생성기가 generated 문서 규칙과 충돌하지 않는가
- [x] API 타입 생성과 호환성 검사가 M0/M1 경계에 맞는가
- [x] 공통 UUIDv7·Clock·설정 검증·로그 마스킹이 포함되었는가
- [x] CI가 로컬 검증과 같은 명령을 실행하는가
- [x] M1이 바로 시작될 수 있는가
- [x] 6~7일 규모를 넘지 않는가

### 11.2 검토 결과

- M0는 기능 구현이 아니라 기반 작업으로 유지합니다.
- Spring Session JDBC 스키마는 M0가 아니라 M1로 미룹니다. 인증이 없는 M0에서 세션 테이블을 먼저 만들 이유가 없습니다.
- OpenAPI와 이벤트 계약은 M0에서 완성하지 않고, 생성기와 검사만 만듭니다.
- 데이터 사전 생성기는 M0에서 빈 스키마로 실행해 보고, M1부터 실제 테이블을 출력합니다.
- 프론트엔드는 API 호출 없이 앱 셸과 공통 오류 처리만 준비하되, OpenAPI 타입 생성 경로는 먼저 만듭니다.
- UUIDv7, Clock, 설정 검증, 로그 마스킹은 M0에 넣어 이후 마일스톤의 중복 구현을 막습니다.
- ktlint, detekt, ESLint, Prettier를 M0 CI gate에 넣습니다.
- API 타입 생성과 OpenAPI 호환성 검사는 M0에서 골격을 만들고 M1부터 필수로 적용합니다.
- M4와 M5의 병렬 가능성은 M0 기반 작업과 무관하므로 M0 범위를 늘리지 않습니다.

### 11.3 실행 후 확인

- M0는 인증·도메인 API를 포함하지 않았고, `GET /api/v1/info`만 추가했습니다.
- 인프라, 백엔드, 프론트엔드, 계약 미리보기를 각각 독립 명령으로 검증했습니다.
- Testcontainers가 실제 PostgreSQL·RabbitMQ 컨테이너를 사용합니다.
- 계약 미리보기는 [ADR-015](../decisions/ADR-015-contract-preview-mode.md)에 따라 `docs/generated/` 초안과 충돌하지 않습니다.
- UUIDv7, Clock, 설정 검증, `request_id`, 로그 마스킹, problem+json을 M0에 포함했습니다.
- CI는 로컬 검증과 같은 Gradle·pnpm·문서 명령을 실행합니다. 원격 실행은 push 후 확인합니다.
- M1은 `app_user`·`space`·Spring Session JDBC부터 시작할 수 있습니다.

## 12. 다음 단계

M0 완료 후 M1 상세 계획을 작성합니다.

M1 상세 계획에 포함할 항목:

- `app_user`, `space`, `invite_code`, `invite_code_use`, `password_reset_token` DDL
- Spring Session JDBC 스키마
- 세션 쿠키, CSRF, 세션 회전
- 가입·로그인·로그아웃·내 정보·Space 이름 수정
- 초대코드 설정과 운영 CLI
- 비밀번호 재설정
- 로그인·초대코드 레이트리밋
- 인증 화면과 설정 화면
- 인증·세션·CSRF 테스트

## 13. 링크

- [M0 기반 부트스트랩](PLAN-001-foundation.md)
- [구현 로드맵](../implementation-roadmap.md)
- [운영과 검증](../operations.md)
- [아키텍처](../architecture.md)
- [API와 이벤트 계약](../contracts.md)
- [데이터 사전](../generated/data-dictionary.md)
