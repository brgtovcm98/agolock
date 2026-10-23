# AGO Lock 개인 물품 관리 앱

계층 구조 기반 개인 물품 관리 앱입니다.

- 물건(Item 마스터)과 재고(Inventory 개별 인스턴스)를 분리합니다.
- Room, Shelf, Box 계층과 공유 권한을 지원합니다.
- PostgreSQL과 pgvector를 사용해 이미지 유사도 검색을 설계합니다.
- AI 인식과 임베딩은 비동기 job으로 처리합니다.

## 현재 상태

- M0 기반 부트스트랩, M1 Identity·Space, M2 계층·권한 기반을 구현했습니다.
- 로컬 인프라, Spring Boot 백엔드, React 프론트엔드, E안 디자인 시스템, 계약 생성기, 문서 검사, CI가 있습니다.
- M1 가입·로그인·세션·CSRF·초대코드·비밀번호 재설정·설정 화면이 동작합니다.
- M2 Room·Shelf·Box CRUD·이동·삭제 차단, 커서 페이지네이션, Idempotency-Key, 공간 트리가 동작합니다.
- M3 카테고리·Item·Inventory·이동 이력과 M4 공유·초대·Grant·감사/Outbox 기반을 구현했습니다.
- M5 이미지 presign·갤러리·worker 기반과 M5.5 P0(Item OWNER 권한, S3 health, worker 분리, Item 갤러리, 업로드 상태)을 구현했습니다.
- P1 재고 편집·이동 이력·물건 수정·카테고리 병합·문맥형 공유·공유 위치 등록을 구현했습니다.
- 공유 사용자는 기존 Room·Inventory 상세, shared MANAGE Inventory 생성·이동·삭제를 사용할 수 있습니다.
- 초대 메일은 Outbox 릴레이로 발송되며 `/invitations#token=...` 링크에서 수락·거절합니다.
- staging 배포 골격(Dockerfile·nginx·Compose·프로필·GHCR workflow)을 추가했습니다.
- M6 검색의 SQL 그룹 집계·keyset cursor·복수 토큰·trigram 검색·추천 ACL·검색어 없는 필터를 구현했습니다. 로컬 검색 성능 gate를 통과했으며 릴리스 운영 검증이 남아 있습니다.
- Box 이동 UI를 구현했습니다. 다음 1차 작업은 M6 잔여 검증과 1차 프리즈, VPS staging 배포입니다. M7 AI는 2차입니다.
- 제품 기준 문서는 [docs/product-spec.md](docs/product-spec.md)입니다.
- AI 작업 규칙은 [AGENTS.md](AGENTS.md)를 따릅니다.

| M0 완료 항목 | 상태 |
|---|---|
| Docker Compose 인프라 4종 healthy | 완료 |
| Spring Boot·MyBatis·Flyway·Testcontainers 스모크 | 완료 |
| UUIDv7, Clock, 설정 검증, request_id, 로그 마스킹 | 완료 |
| React·Vite·Tailwind·E안 디자인 토큰·3개 화면 미리보기 | 완료 |
| OpenAPI·이벤트·데이터 사전 미리보기 생성기 | 완료 |
| `docs_check.py` 계약 검사 확장 | 완료 |
| docs·backend·frontend·contract CI workflow | 작성 완료, GitHub 원격 실행 대기 |

## 빠른 시작

### 1. 로컬 도구

문서 기준 버전은 Java 21, Node 22, pnpm 10입니다. 저장소 안 `.tools/`에 고정 버전을 설치합니다.

```bash
bash scripts/install-tools.sh
source scripts/env.sh
```

- Java 21.0.12.1, Node 22.23.3, pnpm 10.34.7, Gradle 8.14.3을 사용합니다.
- Python은 3.12 이상이면 됩니다. 로컬 검증은 3.14, CI는 3.12입니다.
- `.tools/`, `.python-packages/`, `.gradle-user/`는 Git에 커밋하지 않습니다.

`mise`를 사용하는 경우 루트의 `mise.toml`이 JDK 21을 선택합니다. `.tools/` 방식과 둘 중 하나만 사용합니다.

```bash
mise trust mise.toml
mise install
mise exec -- java -version
```

`mise`는 현재 Java 21 계열을 설치하며, 정확한 21.0.12.1 고정이 필요하면 `.tools/` 방식을 사용합니다.

### 2. 로컬 인프라

상세한 실행·확인·초기화 절차는 [로컬 실행 Runbook](docs/local-runbook.md)을 따릅니다.

```bash
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

| 서비스 | 이미지 | 포트 |
|---|---|---|
| PostgreSQL + pgvector | `pgvector/pgvector:0.8.7-pg16` | 5432 |
| RabbitMQ | `rabbitmq:3.13.7-management` | 5672, 15672 |
| MinIO | `bitnamilegacy/minio:2025.7.23-debian-12-r5` | 9000, 9001 |
| Mailpit | `axllent/mailpit:v1.31.6` | 1025, 8025 |

- 공식 `minio/minio` 이미지는 Docker Hub에서 더 이상 제공되지 않아 로컬 전용으로 고정 태그의 Bitnami legacy 이미지를 사용합니다. 운영 이미지가 아닙니다.
- MinIO 버킷 `agolock-local`은 `minio-init`이 비공개로 생성합니다.
- 로컬 기본 자격증명은 `.env.local.example`과 Compose에만 있으며 실제 비밀 값이 아닙니다.

### 3. 백엔드

```bash
source scripts/env.sh
./gradlew :backend:test
./gradlew :backend:bootRun --args='--spring.profiles.active=local'
```

- `http://localhost:9090/actuator/health/readiness`에서 readiness를 확인합니다.
- `http://localhost:8080/api/v1/auth/config`에서 인증 설정을 확인합니다.
- 로컬 가입은 초대코드가 필요합니다. 아래 CLI로 코드를 만들 수 있습니다.

```bash
./gradlew :backend:bootRun --args='--spring.profiles.active=local,cli invite-code create --label local --max-uses 5 --expires-in 7d'
```
- `http://localhost:8080/swagger-ui.html`에서 런타임 OpenAPI를 확인합니다.
- Flyway는 `V1__extensions.sql`로 `pg_trgm`, `vector`, `citext`를 준비합니다.

### 4. 프론트엔드

```bash
source scripts/env.sh
pnpm install
pnpm dev
```

- `http://127.0.0.1:5173`에서 로그인·회원가입·M1 홈을 확인합니다.
- `http://127.0.0.1:5173/design-preview`에서 채택된 E안의 홈·공간·재고 상세를 모바일 390px, 데스크톱 1280px, 라이트·다크로 확인합니다.
- 로그인 후 `/settings`에서 표시 이름·Space 이름·테마·로그아웃을 관리합니다.
- 미리보기는 실제 API를 호출하지 않는 목데이터 화면입니다.
- E2E를 처음 실행할 때 브라우저를 설치합니다.

```bash
pnpm exec playwright install chromium
pnpm test:e2e
```

### 5. 배포

- staging 배포 골격은 [deploy/README.md](deploy/README.md)를 따릅니다.
- backend/frontend 이미지, nginx, Compose, staging·production 프로필이 있습니다.
- 실제 production 배포는 1차 프리즈와 M8 gate 이후에 수행합니다.

### 6. 계약 미리보기

```bash
source scripts/env.sh
bash scripts/docs_generate.sh all
python3 scripts/docs_check.py
```

- M0는 미리보기 모드입니다. 결과는 `build/contracts/`에만 쓰고 `docs/generated/`의 상세 설계 초안은 덮어쓰지 않습니다 ([ADR-015](docs/decisions/ADR-015-contract-preview-mode.md)).
- `all`은 데이터 사전 생성을 위해 PostgreSQL이 실행 중이어야 합니다.
- M1·M2에서도 `build/contracts/` 미리보기 모드를 유지합니다. 기록 모드 전환은 M4 또는 M8에서 판단합니다.

## 검증 명령

```bash
source scripts/env.sh

# 문서·계약
python3 scripts/docs_check.py
bash scripts/docs_generate.sh all

# 백엔드
./gradlew :backend:ktlintCheck :backend:detekt :backend:test
./gradlew :backend:generateOpenApiPreview

# 프론트엔드
pnpm lint
pnpm test
pnpm build
pnpm exec playwright install chromium
pnpm test:e2e
```

## 저장소 구조

```text
backend/        Kotlin + Spring Boot, Flyway, MyBatis, Testcontainers
frontend/       React + TypeScript + Vite, E안 디자인 시스템
design/         A/B/C/E 시안 HTML, CSS, 스크린샷
infra/          Docker Compose, MinIO 초기화
scripts/        로컬 도구, 계약 생성·검사, 문서 영향 분석
docs/           제품 사양, 설계, ADR, 구현 계획, generated 초안
```

- Gradle wrapper는 루트에 있고 `backend` 하위 프로젝트를 실행합니다.
- `./gradlew test`는 하위 프로젝트의 테스트를 실행합니다.
- 프론트엔드는 pnpm workspace의 단일 패키지입니다.

## 핵심 결정

| 영역 | 결정 |
|---|---|
| 테넌트 | Space 단위, 공유 DB·공유 스키마, `space_id`, 복합 FK, 애플리케이션 ACL |
| 위치 모델 | Inventory가 `room_id`, `shelf_id`, `box_id` 중 정확히 하나를 가짐 |
| 공유 | 초대 수락 후 효력, 직접 공유는 1차 제외 |
| 공유 권한 | `VIEW < EDIT < MANAGE` 누적, 다중 grant는 최대 권한 적용 |
| Item 생성 | 신규 Item은 소유자만, 공유 MANAGE는 기존 Item에 Inventory 추가 |
| 인증 | 이메일+비밀번호, 설정 기반 초대코드, HttpOnly 세션 쿠키 |
| 데이터 접근 | MyBatis + Flyway |
| 비동기 | RabbitMQ topic exchange + Transactional Outbox |
| 검색 | PostgreSQL `pg_trgm` + `simple` FTS, Item 그룹 커서 |
| 이미지 검색 | Gemini 멀티모달 임베딩, pgvector HNSW, 동기 5초 타임아웃 |
| 이미지 저장 | S3 호환 스토리지 + presigned URL, UPLOADING/DRAFT 수명주기 |
| AI | 비동기 job, 사용자 확인 후 저장, 모델 간 임베딩 fallback 금지 |
| 가격 | KRW 단일 통화 |
| 알림 | 1차 이메일 전용, 인앱은 3차 |
| 디자인 | E안 네오 브루탈, Noto Sans KR 셀프 호스팅, 라이트·다크 |
| 저장소 구조 | Kotlin/Spring Boot + React monorepo |

## 문서 지도

| 문서 | 역할 |
|---|---|
| [docs/product-spec.md](docs/product-spec.md) | 요구사항, 범위, 확정된 제품 결정 |
| [docs/architecture.md](docs/architecture.md) | 시스템 경계, 모듈, 테넌트, 인증, 보안 불변식 |
| [docs/domain.md](docs/domain.md) | 계층, 위치, 마스터·재고, 상태, 카테고리, 이동 |
| [docs/sharing.md](docs/sharing.md) | 공유, 초대, 권한 매트릭스, 상속 |
| [docs/search.md](docs/search.md) | 키워드 검색, 필터, 최근 검색, 자주 본 물건 |
| [docs/media-ai.md](docs/media-ai.md) | 이미지, 임베딩, AI 인식, MQ 파이프라인 |
| [docs/contracts.md](docs/contracts.md) | API·이벤트 규칙과 계약 문서 링크 |
| [docs/frontend.md](docs/frontend.md) | 화면, 내비게이션, 사용자 흐름, UI 상태 |
| [docs/design-system.md](docs/design-system.md) | E안 토큰, 컴포넌트, 반응형, 다크 모드, 접근성 |
| [docs/operations.md](docs/operations.md) | 인프라, 배포, 보안, 테스트, 관측, 제품 단계 로드맵 |
| [docs/implementation-roadmap.md](docs/implementation-roadmap.md) | 구현 마일스톤, 의존성, 완료 조건 |
| [docs/implementation/PLAN-001-foundation.md](docs/implementation/PLAN-001-foundation.md) | M0 기반 부트스트랩 상세 구현 계획 |
| [docs/implementation/PLAN-001-foundation-delivery.md](docs/implementation/PLAN-001-foundation-delivery.md) | M0 계약·CI·검증 계획 |
| [PLAN-002](docs/implementation/PLAN-002-identity-space.md) | M1 Identity·Space 상세 계획 |
| [PLAN-003](docs/implementation/PLAN-003-hierarchy-permissions.md) | M2 계층·권한 상세 계획 |
| [PLAN-004](docs/implementation/PLAN-004-inventory.md) | M3 Category·Item·Inventory 상세 계획 |
| [PLAN-005](docs/implementation/PLAN-005-sharing.md) | M4 공유·초대 상세 계획 |
| [PLAN-006](docs/implementation/PLAN-006-media-messaging.md) | M5 이미지·MQ 상세 계획 |
| [PLAN-007](docs/implementation/PLAN-007-search.md) | M6 검색·추천 상세 계획 |
| [PLAN-008](docs/implementation/PLAN-008-ai-image-search.md) | M7 AI·이미지 검색 상세 계획 |
| [PLAN-009](docs/implementation/PLAN-009-release-hardening.md) | M8 출시 경화 상세 계획 |
| [docs/generated/data-dictionary.md](docs/generated/data-dictionary.md) | 테이블·컬럼·인덱스·제약 (설계 초안) |
| [docs/generated/openapi.yaml](docs/generated/openapi.yaml) | HTTP API 계약 (설계 초안) |
| [docs/generated/events.yaml](docs/generated/events.yaml) | 비동기 이벤트 계약 (설계 초안) |
| [docs/decisions/README.md](docs/decisions/README.md) | ADR 목록과 결정 요약 |
| [AGENTS.md](AGENTS.md) | AI 읽기 순서와 문서 갱신 규칙 |
| [docs/index.yaml](docs/index.yaml) | AI·자동화용 문서 지도 |

## 읽는 순서

- 제품·기획: 제품 사양 → 화면·흐름 → 디자인 시스템 → 로드맵
- 백엔드: 아키텍처 → 도메인 → 데이터 사전 → 기능 문서 → API·이벤트 계약
- 프론트엔드: 화면·흐름 → 디자인 시스템 → API 계약 → 검색·공유·미디어 문서
- 인프라·운영: 아키텍처 → 운영 → 보안 → 테스트
- 구현: 구현 로드맵 → 첫 마일스톤 상세 계획 → 운영·계약

## 문서 갱신 원칙

- 스키마의 단일 원본은 `docs/generated/data-dictionary.md`입니다.
- API의 단일 원본은 `docs/generated/openapi.yaml`입니다.
- 이벤트의 단일 원본은 `docs/generated/events.yaml`입니다.
- 설계 결정은 ADR에 남기고, 설계 문서는 ADR을 링크합니다.
- 같은 내용을 여러 문서에 복사하지 않습니다.
- `docs/generated/` 아래 문서는 구현 후 생성 스크립트로 갱신합니다.
- M0에서는 [ADR-015](docs/decisions/ADR-015-contract-preview-mode.md)에 따라 미리보기를 `build/contracts/`에만 생성합니다.
- 코드 변경 후 [AGENTS.md](AGENTS.md)의 문서 갱신 절차를 따릅니다.

## 다음 단계

- M6 최근 검색 보관·정리와 추천 4종, 생성 계약 기록 모드를 마감합니다.
- 이미지 실패·만료·재시도 E2E를 보강하고 1차 프리즈를 진행합니다.
- VPS의 도메인·TLS·시크릿을 연결해 staging 배포와 실제 스모크를 수행합니다.
- 백업·복구·롤백, 관측·보안·접근성·PWA 출시 gate를 검증합니다.

실행 순서와 완료 조건은 [1차 릴리스 계획](docs/implementation/PLAN-011-first-release-delivery.md)을 따릅니다.
