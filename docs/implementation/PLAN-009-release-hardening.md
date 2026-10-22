# PLAN-009: M8 출시 경화와 운영 상세 계획

> 상태: 검토 대기 (M8 착수 전 승인 필요)
> 역할: 구현 로드맵 M8의 E2E, 회귀, 보안, 성능, 접근성·PWA, 관측, 백업, 배포, 공급망, 계약 자동화, 출시 체크리스트 계획
> 상위 문서: [구현 로드맵](../implementation-roadmap.md) 4번 항목 M8
> 선행 계획: [PLAN-001 기반](PLAN-001-foundation.md), [PLAN-001 계약·CI](PLAN-001-foundation-delivery.md)
> 관련 문서: [운영](../operations.md), [계약](../contracts.md), [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md), [미디어·AI](../media-ai.md), [검색](../search.md), [아키텍처](../architecture.md), [ADR-014](../decisions/ADR-014-generated-docs-draft.md), [ADR-015](../decisions/ADR-015-contract-preview-mode.md), [ADR-016](../decisions/ADR-016-neo-brutal-design-system.md)

## 1. 목적·범위·비목표

M8은 새 기능이 아니라 M0~M7의 결과를 출시 가능한 품질과 운영 절차로 고정하는 지속 경화 단계입니다. 수동 검증을 CI gate와 리허설로 옮기고, 계약 문서를 설계 초안에서 코드 생성물로 전환합니다.

- 범위: Playwright E2E, 교차 테넌트·권한·이미지·검색·AI 회귀, 부하·슬로우 쿼리, 접근성·PWA, 백업·복구, 배포·롤백·헬스, Sentry·대시보드·알림, 의존성·비밀 스캔, OpenAPI 호환성, generated 자동화, 1차·2차 출시 체크리스트.
- 비목표: 새 화면·API·도메인 규칙, 3차 기능, RLS, Redis 레이트리밋, 오프라인 편집, 네이티브 앱, 성능 리팩터링 자체.
- 제품 규칙·화면·권한·백업 정책은 다시 정의하지 않고 [운영](../operations.md)과 [화면과 흐름](../frontend.md)을 링크합니다.

## 2. 진입·종료 조건과 선행 의존성

### 2.1 진입 조건

| 선행 | 재사용하는 결과 | 증거 |
|---|---|---|
| M0 | CI 4개 job, 계약 미리보기, 문서 검사 | `ci.yml` 통과 |
| M1 | 세션·CSRF·초대코드·재설정 | 인증 통합 테스트 |
| M2 | 계층, `effectivePermission`, 상속 | 계층·권한 테스트 |
| M3 | Item·Inventory·오버라이드·상태·이동 | `version` 충돌 테스트 |
| M4 | 초대 전용 공유, Grant, 감사 | 수락 전·철회 후 차단 |
| M5 | presign, worker, Outbox·DLQ | DLQ 재처리 |
| M6 | 검색 커서·필터·추천 | 검색 p95, 실행 계획 |
| M7 | AI job, 임베딩, 이미지 검색 | 멱등성·취소·모델 필터 |

- M8.0에서 staging, 시크릿 관리자, Sentry, 알림 채널을 준비합니다. 환경 구분은 [운영](../operations.md) 1번 항목을 따릅니다.
- `docs/index.yaml`의 `PLAN_009` 등록은 M8.0에서 문서 소유자가 수행하며, 이 계획은 인덱스를 수정하지 않습니다.

### 2.2 종료 조건

| ID | 조건 | 판정 |
|---|---|---|
| G1 | 핵심 E2E PR 통과, nightly 전체 14일 연속 green | 실행 이력 |
| G2 | 테넌트·권한·이미지·검색·AI 회귀가 CI에 포함 | 테스트 목록 |
| G3 | 성능 기준, axe 심각·중대 0건, PWA 설치 요건 | k6·axe·Lighthouse |
| G4 | 복구 훈련·롤백 리허설 각 1회 | 훈련 기록 |
| G5 | staging·production 동일 스크립트 배포, 헬스·스모크 통과 | 배포 로그 |
| G6 | 대시보드·Sentry·알림 staging 수신 | 알림 테스트 |
| G7 | 비밀·취약점 gate가 PR 차단 | 고의 실패 PR |
| G8 | OpenAPI 호환성·generated diff gate 동작 | breaking 테스트 PR |
| G9 | 1차·2차 출시 체크리스트 서명 | 체크리스트 PR |

## 3. 작업 흐름

| ID | 흐름 | 핵심 작업·산출물 | gate |
|---|---|---|---|
| W1 | E2E·회귀 | fixture·API 시드·핵심 spec, `frontend/playwright.config.ts` | PR·nightly |
| W2 | 보안·테넌트 | `backend/src/test/kotlin/com/agolock/security/**`, 이미지 접근·철회·레이트리밋 | PR |
| W3 | 성능 | `scripts/perf/**`, 시드, 슬로우 쿼리 리포트 | nightly·release |
| W4 | 접근성·PWA | `e2e/a11y.spec.ts`, `e2e/pwa.spec.ts`, `manifest.webmanifest`, `lighthouserc.json` | PR·nightly |
| W5 | 관측 | `infra/observability/**`, Prometheus 메트릭, Sentry, 알림 | nightly·release |
| W6 | 백업·복구 | `scripts/ops/restore_drill.sh`, `verify_restore.sql` | 분기·release |
| W7 | 배포·롤백 | `Dockerfile`, `scripts/ops/{deploy,rollback,healthcheck,smoke}.sh` | release |
| W8 | 공급망 | `.gitleaks.toml`, `gradle/verification-metadata.xml`, Trivy, SBOM | PR·release |
| W9 | 계약 자동화 | `docs_generate.sh`, `docs_check.py`, `check_openapi_compat.sh` | PR |
| W10 | 출시 체크리스트 | `release.yml`, 1차·2차 리허설 | release |

- W1·W2 후 W4·W5·W8을 병렬 진행하고, W3은 staging, W6·W7은 인프라 권한이 준비된 뒤 시작합니다.
- 테스트마다 독립 Space를 만들고 소유자와 초대 사용자를 분리합니다. AI provider는 fake/stub을 쓰고 실 호출은 nightly 1회로 제한합니다.
- 고정 대기(`waitForTimeout`)를 금지하고 role·label·`data-testid`로 대기합니다. flaky는 이슈 번호와 함께 `test.fixme`로 격리하고 2주 내 수정하며 비율은 1% 미만을 유지합니다.

## 4. CI/CD 파이프라인과 gate

### 4.1 workflow

| 파일 | 역할 | 트리거 |
|---|---|---|
| `.github/workflows/ci.yml` | 기존 4개 job에 `e2e`, `a11y`, `security` 추가 | push `main`, PR |
| `.github/workflows/nightly.yml` | 전체 브라우저 E2E, 부하, Lighthouse, 이미지 스캔, 복구 dry-run | 매일 03:00 KST |
| `.github/workflows/release.yml` | 이미지 빌드·서명, staging, 승인 후 production, 태그 | `workflow_dispatch` |
| `.github/workflows/docs.yml` | 문서 검사 (기존 유지) | `docs/**`, `scripts/**` |

### 4.2 gate 구분

| gate | PR | nightly | release |
|---|---|---|---|
| 문서·계약 | `python3 scripts/docs_check.py` | 동일 | 동일 |
| 백엔드 | `./gradlew :backend:ktlintCheck :backend:detekt :backend:test` | + 반복 2회 | 동일 |
| 프론트엔드 | `pnpm lint && pnpm format:check && pnpm test && pnpm build` | 동일 | 동일 |
| E2E | chromium 데스크톱·모바일, 핵심 8흐름, shard 2 | 전체 spec × chromium·webkit·firefox, `--repeat-each=3` | staging 전체 |
| 접근성 | axe 심각·중대 0건 (핵심 6화면) | 전 화면 + Lighthouse | staging 수동 |
| 성능 | 없음 | k6 부하·소크, 슬로우 쿼리 | 기준 미달 시 배포 중단 |
| 보안 | gitleaks, `pnpm audit --prod`, Trivy fs | 전체 이력, Trivy image, SBOM | 이미지 서명·SBOM |
| 호환성 | oasdiff, generated diff, `schemaVersion` | 릴리스 태그 대비 | 태그 고정 |

### 4.3 명령 매트릭스

| 목적 | 명령 | 예산 |
|---|---|---|
| 계약 미리보기 | `./gradlew :backend:generateOpenApiPreview && python3 scripts/normalize_openapi.py && python3 scripts/export_events.py` | 4분 |
| 계약 기록 | `bash scripts/docs_generate.sh all` | 6분 |
| 호환성 | `bash scripts/check_openapi_compat.sh origin/main` | 1분 |
| E2E PR | `pnpm exec playwright test --project=desktop-chromium --project=mobile-chromium --shard=1/2` | 12분 |
| E2E 전체 | `pnpm exec playwright test --repeat-each=3 --workers=4` | 45분 |
| 접근성 | `pnpm exec playwright test e2e/a11y.spec.ts` | 5분 |
| Lighthouse | `pnpm exec lhci autorun` | 8분 |
| 부하 | `k6 run scripts/perf/search-load.js` | 20분 |
| 이미지 스캔 | `trivy image --severity CRITICAL,HIGH --exit-code 1 ghcr.io/agolock/agolock:$TAG` | 3분 |
| 복구 훈련 | `bash scripts/ops/restore_drill.sh --target staging --dry-run` | 30분 |

- PR 20분, nightly 90분, release 60분을 넘지 않게 하고 같은 커밋의 중복 실행은 `concurrency`로 취소합니다.

### 4.4 artifact·환경·시크릿

| artifact | 보존 |
|---|---|
| `playwright-report`(trace·영상), `axe-report`, `lighthouse` | 14~30일 |
| `k6-summary`, `trivy-sarif`, `oasdiff-report`, `generated-diff` | 30~90일 |
| `sbom`(CycloneDX), `smoke-log` | 릴리스 영구 / 180일 |

| 환경 | 배포 | 보호 |
|---|---|---|
| local | Compose + `bootRun` + Vite | 없음 |
| staging | `release.yml` 자동 | 승인 1명, 대기 5분 |
| production | staging 스모크 후 수동 | 승인 2명, 대기 10분, `main` 태그만 |

- 시크릿은 `STAGING_*`, `PROD_*`, `SENTRY_AUTH_TOKEN`, `SLACK_WEBHOOK_URL`, `GHCR_TOKEN`을 environment 단위로 두고 용도·소유자·회전 주기를 이슈에 기록합니다.
- 배포 스크립트에서 `set -x`를 쓰지 않습니다. 필수 check는 `docs`, `backend`, `frontend`, `contract`, `e2e`, `security`, `a11y`입니다.

## 5. 테스트 매트릭스

### 5.1 흐름·화면·브라우저

D는 desktop-chromium 1280×800, M은 mobile-chromium Pixel 7 412×915, W는 mobile-webkit iPhone 13 390×844, F는 desktop-firefox입니다.

| ID | 흐름 (화면) | spec | PR | nightly |
|---|---|---|---|---|
| E2E-AUTH-01 | 가입·로그인·로그아웃·세션 만료 (SCR-001, 002) | `auth.spec.ts` | D, M | D, M, W, F |
| E2E-AUTH-02 | 비밀번호 재설정·Mailpit 확인 (SCR-001) | `auth.spec.ts` | D | D, M, W |
| E2E-HIER-01 | Room·Shelf·Box 생성·이동 (SCR-004~007) | `hierarchy.spec.ts` | D, M | D, M, W |
| E2E-INV-01 | Item+첫 Inventory, 오버라이드 (SCR-009~011) | `inventory.spec.ts` | D, M | D, M, W, F |
| E2E-INV-02 | 상태·폐기 확인·이동·이력·no-op (SCR-009, 010) | `inventory.spec.ts` | D | D, M, W |
| E2E-SHR-01 | 초대·수락·권한 배지 (SCR-015, 016, 019) | `sharing.spec.ts` | D, M | D, M, W |
| E2E-SHR-02 | 거절·철회·MANAGE 제한 (SCR-011, 012, 015) | `sharing.spec.ts` | D | D, M, W |
| E2E-MED-01 | 업로드·대표 승격·DRAFT 연결 (SCR-010, 014) | `media.spec.ts` | D | D, M, W |
| E2E-SRH-01 | 검색·필터·그룹 커서·5개 제한 (SCR-003, 008) | `search.spec.ts` | D, M | D, M, W, F |
| E2E-SRH-02 | 이미지 검색·타임아웃 안내 (SCR-013) | `search.spec.ts` | D | D, M |
| E2E-AI-01 | AI 제안·확인 저장·취소 (SCR-014) | `ai.spec.ts` | D | D, M |
| E2E-ERR-01 | 403·404·409·422·429·503 (SCR-018) | `errors.spec.ts` | D | D, M, W |

- 뷰포트는 [디자인 시스템](../design-system.md) 8번 항목의 390px·1280px 기준을 따릅니다.
- PR은 권한·데이터 손실과 직결된 8개 흐름만 실행합니다. nightly 3회 연속 실패 시 `main` 머지를 중지합니다.
- PWA·접근성 spec은 5.4의 검사 항목과 함께 nightly에서 실행합니다.

### 5.2 테넌트·권한 사례

| 사례 | 기대 |
|---|---|
| 다른 Space Room·Inventory 조회·수정, `space_id` 위조 | 404 또는 400, DB 미변경 |
| 초대 수락 전 대상 Room 조회 | 404 |
| VIEW의 PATCH, EDIT의 Item 마스터 PATCH | 403 `PERMISSION_DENIED` |
| MANAGE의 신규 Item 생성 / 기존 Item Inventory 추가 | 403 / 201 |
| Room Grant 상속 (Shelf·Box·Inventory) | 200, `isShared=true` |
| Room VIEW + Shelf MANAGE | MANAGE |
| Grant 철회 후 새 요청 / 기존 presigned URL | 404 / TTL까지 200 |
| EDIT의 Item 이미지 presign / Inventory 이미지 presign | 403 / 201 |
| 초대 이메일 불일치, 세션 만료, CSRF 누락 | 해당 오류 코드, 401, 403 |
| 로그인 11회, 이미지 검색 11회 | 429, `Retry-After` |

### 5.3 이미지·검색·AI 실패 사례

| 사례 | 기대 |
|---|---|
| 매직 바이트 불일치, `objectKey` 불일치 | complete 422, `FAILED`, 원본 미노출 |
| 10MB 초과 / 11번째 이미지 | 422 / `IMAGE_LIMIT_EXCEEDED` |
| presign 만료 후 PUT | S3 403, `UPLOADING` 유지 후 정리 job 삭제 |
| 후처리 실패 | `FAILED`, 대표 승격 없음, DLQ 알림 |
| soft delete 30일 경과 (Clock 주입) | 원본·썸네일 물리 삭제 |
| 이미지 검색 5초 초과 / 모델·버전 불일치 | 503 `IMAGE_SEARCH_UNAVAILABLE` / 빈 결과 |
| AI provider 429 | `AI_QUOTA_EXCEEDED`, 수동 저장 가능 |
| AI 취소 후 늦은 결과 / DLQ 재주입 | 저장 안 함 / 중복 없음 |
| MQ 중단 / Outbox 발행 실패 | API 201+`PENDING` 후 발행 / 재시도·`FAILED` 알림 |

### 5.4 성능·접근성·PWA 기준

| 구분 | 기준 |
|---|---|
| 검색 (Space 20 × Inventory 5,000) | p95 300ms 이하 ([검색](../search.md) 13번 항목), p99 800ms |
| 목록·상세·쓰기 | p95 400ms / 250ms / 600ms, 5xx 0.5% 미만 |
| 이미지 검색 / 소크·피크 | p95 3초·p99 5초·타임아웃 1% 미만 / 100 VU 30분·300 VU 10분 |
| 슬로우 쿼리 | 500ms 초과 0건, `pg_stat_statements` 상위 20개 검토 |
| 프론트엔드 | LCP 2.5초, CLS 0.1, INP 200ms, Lighthouse 85점 이상 |
| axe | critical·serious 0건, 19개 화면 |
| 키보드·포커스·이름 | Tab·Enter·Esc 완료, 대화상자 트랩, label·alt, 44px |
| 상태·모션·명암비 | `aria-live`, `prefers-reduced-motion`, 4.5:1 |
| 스크린리더 | SCR-003·009·014·016을 NVDA·VoiceOver로 확인 |
| PWA | manifest(192/512/maskable), 앱 셸만 캐시, API·인증 캐시 금지, 설치·iOS 안내 |

- 성능 시드는 `scripts/perf/seed.sql`을 쓰고 staging 고정 인스턴스에서 측정해 사양을 기록합니다. 기준 미달 시 인덱스·쿼리 수정 또는 읽기 모델 도입을 별도 ADR로 판단합니다.
- 접근성 목표는 WCAG 2.2 AA이며 토큰 명암비는 [디자인 시스템](../design-system.md) 4번 항목을 따릅니다. 수동 점검은 화면 ID별로 PR에 기록합니다.

## 6. 운영 런북

### 6.1 배포

배포 순서와 롤백 원칙은 [운영](../operations.md) 4번 항목을 따르고 아래는 실행 단위입니다.

| 순서 | 명령·확인 | 실패 시 |
|---|---|---|
| 1 | CI green, 릴리스 태그, 변경 목록 확인 | 중단 |
| 2 | `docker build -t ghcr.io/agolock/agolock:$TAG .` 후 push | 중단 |
| 3 | `bash scripts/ops/deploy.sh staging $TAG` (Flyway → api → worker) | 마이그레이션 실패 시 중단 |
| 4 | `bash scripts/ops/healthcheck.sh` (compose 내부 9090) | 롤백 |
| 5 | `bash scripts/ops/smoke.sh https://staging.example.com` | 롤백 |
| 6 | 15분 관측(5xx, p95, DLQ, Outbox) 후 production 승인·반복 | 기준 초과 시 롤백 |

- 마이그레이션은 expand-contract만 사용하고 파괴적 변경은 다음 릴리스로 분리합니다. readiness에 AI provider는 포함하지 않습니다.

### 6.2 롤백

| 순서 | 행동 |
|---|---|
| 1 | 5xx 5% 초과 5분, 스모크 실패, 마이그레이션 오류 중 하나면 온콜 책임자가 결정 |
| 2 | `bash scripts/ops/rollback.sh <previous-tag>` 실행, 5분 내 이전 이미지 기동 |
| 3 | 헬스·스모크 재실행, 스키마 비호환이면 forward fix 또는 기능 플래그 |
| 4 | 데이터 손상 의심 시 6.3으로 전환, 원인·영향·복구 시간을 24시간 내 기록 |

- 다운 마이그레이션은 금지하고 이전 이미지 태그는 2개 릴리스 동안 보관합니다. 리허설은 릴리스마다 staging에서 1회 수행합니다.

### 6.3 백업·복구 훈련

복구 목표와 주기는 [운영](../operations.md) 3번 항목을 따르고 이 문서는 절차만 정의합니다.

| 순서 | 행동 | 증거 |
|---|---|---|
| 1 | 일정 공지, staging 쓰기 중지, ephemeral PostgreSQL 생성 | 인스턴스 ID |
| 2 | 최신 전체 백업+WAL로 목표 시점 복구, S3 객체 1개 버전 복원 | 백업 ID |
| 3 | `psql -f scripts/ops/verify_restore.sql` (행 수, FK, Flyway, 체크섬) | 쿼리 결과 |
| 4 | 복구 DB로 앱 기동 후 읽기 스모크(로그인·검색·상세) | 스모크 로그 |
| 5 | RPO·RTO 실측값을 운영 기준과 비교, 인스턴스 삭제, 체크리스트 첨부 | 훈련 기록 |

- 첫 훈련은 M8.6에서 수행하고 분기 1회 반복합니다. 실패 시 원인과 재훈련 일정을 기록하고 release를 차단합니다.

### 6.4 장애·알림·DLQ·온콜

| 구분 | 행동 |
|---|---|
| 감지·분류 | 알림 또는 스모크 실패, 5분 내 확인. SEV1 전체·데이터 손실, SEV2 핵심 저하, SEV3 일부, SEV4 문의 |
| 초동·공유 | 대시보드·최근 배포 확인, 필요 시 롤백. SEV1·2는 15분 내 공유, 30분마다 갱신 |
| 종료 | 영향·원인·재발 방지·후속 이슈 등록. 알림 기준은 [운영](../operations.md) 7번 항목 |
| DLQ | `rabbitmqctl list_queues name messages consumers` 확인, `bash scripts/ops/requeue_dlq.sh <queue> --limit 100`로 소량 재처리 |
| Outbox | `SELECT status, count(*) FROM outbox_event GROUP BY status;`, `FAILED`는 원인 조치 후 재발행, `PENDING` 100 미만 유지 |
| 온콜 | 매일 헬스·5xx·DLQ·Outbox·백업·디스크, 매주 취약점·인증서·AI 비용, 매월 롤백 리허설·비밀 회전, 분기 복구·접근성 |

- 재처리는 원인 조치 후 100건 단위로 수행하고 같은 `eventId` 재주입 시 결과가 1건인지 확인합니다. DLQ CLI가 없으면 M8.6에서 추가하며 재처리 API 신설은 별도 ADR로 결정합니다.

## 7. generated 문서 자동화

- 현재는 [ADR-015](../decisions/ADR-015-contract-preview-mode.md)에 따라 `build/contracts/`에만 쓰고 `docs/generated/` 초안을 보존합니다.
- M8에서 [ADR-014](../decisions/ADR-014-generated-docs-draft.md)의 generated 규칙으로 돌아가 `docs/generated/`에 기록합니다. ADR-015를 뒤집으므로 **ADR-017**을 작성해 ADR-015를 `Superseded`로 표시합니다. 번호·시점은 확인 필요이며 M8.7에서 확정합니다.

| 단계 | 작업 | 검증 |
|---|---|---|
| D1 | `scripts/docs_generate.sh`에 기록 모드 추가, 임시 디렉터리 후 rename | 실패 시 기존 파일 유지 |
| D2 | 세 문서 머리말을 설계 초안에서 생성물 표시로 교체 | `docs_check.py`가 생성 머리말 요구 |
| D3 | `docs_check.py`가 `설계 초안` 머리말을 실패 처리하고 생성 명령 안내 | 고의 초안 삽입 시 실패 |
| D4 | PR에서 `bash scripts/docs_generate.sh all` 후 `git diff --exit-code docs/generated` | diff 시 PR 실패 |
| D5 | PostgreSQL에 Flyway 적용 후 데이터 사전 생성, `pnpm generate:api` diff 확인 | `docker compose -f infra/docker-compose.yml up -d postgres` |

- 데이터 사전용 DB 적용은 Gradle `flywayMigrate` task 추가를 권장합니다. 현재 task가 없으면 확인 필요하며 임시로 `bootRun` 1회 기동으로 마이그레이션을 적용합니다.
- 기록 모드에서도 `build/contracts/` 미리보기는 로컬 확인용으로 유지합니다.

| 호환성 대상 | 기준 |
|---|---|
| OpenAPI | `oasdiff breaking --fail-on ERR <base> docs/generated/openapi.yaml`, 기준선은 직전 `release-*` 태그 |
| 이벤트 | `scripts/check_events_compat.py`: `schemaVersion` 증가 없이 필드 삭제·타입 변경 금지 |
| 예외 | `contracts/oasdiff-exceptions.yaml`에 ADR·승인·만료일 기록, 만료 시 재차단 |

| 실패 상황 | 처리 |
|---|---|
| 생성기 실행 실패, DB 연결 실패 | 재시도 2회 후 job 실패, 기존 파일 유지, 건너뛰기 금지 |
| generated diff | PR 작성자가 로컬 재생성 후 커밋 |
| breaking change | 기본 차단, 예외는 ADR·승인·만료일 필요 |
| 수동 편집, 생성기 결함 | 머리말·diff gate로 차단 후 생성 명령 안내 / 이전 커밋 복구 후 수정 PR 우선 |

## 8. 단계별 작업 분해

| 단계 | 작업 | 주요 파일 | 검증 | 예상 |
|---|---|---|---|---|
| M8.0 | staging, Sentry, 시크릿, 브랜치 보호, 필수 check, 인덱스 등록 | environment 설정, `ci.yml` | staging 헬스, 필수 check | 2일 |
| M8.1 | E2E 기반: fixture, API 시드, 프로젝트 확장 | `frontend/e2e/support/*.ts`, `playwright.config.ts` | 로그인·재고 생성 통과 | 3일 |
| M8.2 | 핵심 흐름 spec 8종 | `frontend/e2e/{auth,hierarchy,inventory,sharing,media,search,ai,errors}.spec.ts` | PR E2E 12분 이내 | 5일 |
| M8.3 | 보안·테넌트 회귀 | `backend/src/test/kotlin/com/agolock/security/*.kt`, `media/ImageAccessTest.kt` | 5.2 사례 통과 | 3일 |
| M8.4 | 접근성·PWA | `a11y.spec.ts`, `pwa.spec.ts`, `manifest.webmanifest`, `lighthouserc.json` | axe 0건, 설치 요건 | 4일 |
| M8.5 | 성능·슬로우 쿼리 | `scripts/perf/*.js`, `seed.sql`, `slow_query_report.sql` | p95 기준, 500ms 초과 0건 | 4일 |
| M8.6 | 관측·알림·DLQ·첫 복구 훈련 | `infra/observability/**`, `application.yml`, `requeue_dlq.sh` | 알림 수신, DLQ 재처리 | 3일 |
| M8.7 | 계약 기록 모드·호환성·ADR-017 | `docs_generate.sh`, `docs_check.py`, `check_openapi_compat.sh` | diff·breaking 차단 | 3일 |
| M8.8 | 공급망 | `.gitleaks.toml`, `verification-metadata.xml`, Trivy·SBOM job | 고의 비밀·취약점 차단 | 2일 |
| M8.9 | 배포·롤백·복구 | `Dockerfile`, `scripts/ops/*.sh`, `verify_restore.sql` | staging 배포·롤백 리허설 | 4일 |
| M8.10 | 출시 리허설·체크리스트 | `.github/workflows/release.yml`, 체크리스트 PR | 1차·2차 서명 | 3일 |

- 1인 기준 6주(약 30일)이며 W1·W2와 W4·W5·W8을 병렬화하면 5주로 줄일 수 있습니다. 각 단계는 `python3 scripts/docs_check.py`와 영향 문서 갱신으로 끝냅니다.

## 9. 완료 체크리스트

### 9.1 M8 완료 조건

| 로드맵 조건 | 증거 |
|---|---|
| 운영의 1차·2차 종료 조건 충족 | 9.2 |
| PWA 설치 요건·접근성 통과 | Lighthouse, axe, 수동 체크리스트 |
| 복구 훈련·보안·의존성 점검 기록 | 훈련 기록, Trivy·gitleaks 리포트 |
| generated 재생성·OpenAPI 호환성 gate | 생성 커밋, breaking 테스트 PR |
| 롤백 리허설 | staging 롤백 로그 |

### 9.2 1차·2차 종료 조건

| 1차 | 2차 |
|---|---|
| 핵심 CRUD·권한 테스트 통과 | AI 실패가 재고 저장을 막지 않음 |
| 교차 테넌트 접근 차단 (5.2) | job 멱등성·재처리 (DLQ 재주입) |
| 이미지 업로드·다운로드 권한 | 이미지 검색 ACL 필터 |
| 초대 수락 전 접근 차단 | 임베딩 모델·버전 필터 |

### 9.3 출시 체크리스트

| 1차 | 2차 (1차 전체 포함) |
|---|---|
| 코드 프리즈·릴리스 태그 | AI provider 한도·비용 확인 |
| 마이그레이션 리뷰 (expand-contract) | 이미지 검색 타임아웃·DLQ 알림 |
| 백업·복구 확인 | 임베딩 모델·버전 고정 확인 |
| E2E·성능·접근성 green | AI 비용·품질 벤치마크 기록 |
| 취약점 critical·high 0건 | 2차 종료 조건 증거 첨부 |
| 롤백 리허설, 온콜·공지 준비 | 72시간 관측 (1차는 24시간) |

## 10. 위험·미해결 질문·ADR

| 위험 | 대응 |
|---|---|
| E2E flaky | trace, 재시도 2회, 2주 내 수정, 격리 1% 미만 |
| webkit 차이 | nightly 전체, release 전 필수 |
| 성능 측정 편차 | staging 고정, 사양 기록, 상대 기준 |
| Sentry PII 유출 | URL·이미지 본문·세션 스크러빙 테스트 |
| oasdiff 오탐, 생성기 DB 의존 | 예외 파일·만료일·ADR / postgres service·캐시 |
| 시크릿 분산, 복구 훈련 비용 | environment 시크릿·회전 기록 / ephemeral 인스턴스·야간 |
| iOS PWA 제약 | 홈 화면 추가 안내, 수동 체크리스트 |

| 질문 | 권장안 | 상태 |
|---|---|---|
| 부하 도구 | k6 | 확인 필요 |
| Safari 범위 | nightly·release 필수, PR 제외 | 확인 필요 |
| 서비스 워커 | `vite-plugin-pwa`, 앱 셸만 캐시 | 확인 필요 |
| OpenAPI 비교 | `oasdiff` 버전 고정 | 확인 필요 |
| Sentry 호스팅 | 1차 SaaS | 확인 필요 |
| staging 호스팅 | 관리형 PostgreSQL·S3·RabbitMQ 단일 리전 | 확인 필요 |
| 알림 채널 | Slack 1차, SEV1은 담당자 호출 | 확인 필요 |

| ADR | 내용 | 시점 |
|---|---|---|
| ADR-017 | generated 기록 모드 전환, ADR-015 대체 | M8.7 |
| ADR-018 | E2E 브라우저 매트릭스·flaky 정책 | M8.1 |
| ADR-019 | 성능 기준·측정 환경·부하 도구 | M8.5 |

- ADR 번호는 작성 전에 `docs/decisions/README.md`에서 중복을 확인합니다.

## 11. 링크

- [구현 로드맵](../implementation-roadmap.md)
- [운영과 검증](../operations.md)
- [API와 이벤트 계약](../contracts.md)
- [화면과 흐름](../frontend.md)
- [디자인 시스템](../design-system.md)
- [미디어·AI](../media-ai.md)
- [검색](../search.md)
- [아키텍처](../architecture.md)
- [PLAN-001 기반 부트스트랩](PLAN-001-foundation.md)
- [PLAN-001 계약·CI·검증](PLAN-001-foundation-delivery.md)
- [ADR-014](../decisions/ADR-014-generated-docs-draft.md)
- [ADR-015](../decisions/ADR-015-contract-preview-mode.md)
- [ADR-016](../decisions/ADR-016-neo-brutal-design-system.md)
