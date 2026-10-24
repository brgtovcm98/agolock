# PLAN-015: 현재 구현 현황과 후속 개선 목록

> 상태: 인계 기준 확정 (2026-10-24)
> 역할: 완료 작업의 근거, 남은 개선·검증 항목, 다음 실행 순서의 통합 목록
> 기준: 로컬 `main` `298a2cb`(INV-01 병합), 원격 미반영. 로컬 `origin/main` 추적 ref는 `9e1f066`이라 `main`이 앞서 있습니다
> 범위 원본: [제품 사양](../product-spec.md), [운영](../operations.md)

## 1. 브랜치와 완료 범위

| 구분 | 반영 상태 | 근거 |
|---|---|---|
| 기존 릴리스·UI·권한·검색 보완 | main에 통합됨, 원격 미반영 | `1eeeb70`, 검증 기록 `f27f0f7`; [첫 출시 계획](PLAN-011-first-release-delivery.md) |
| 물건·재고 개선 | main에 반영됨 | `447c971`, `7c42304`; [PLAN-014](PLAN-014-inventory-hardening.md) |
| 물건·재고 항목 작업 | INV-02·INV-01을 항목 브랜치에서 병합했습니다 | `09121d0`(INV-02), `298a2cb`(INV-01); [PLAN-016 6.1·6.2](PLAN-016-item-workflow-and-queue.md) |
| 원격 저장소·배포 | push하지 않았습니다. 로컬 `origin/main` 추적 ref는 `9e1f066`이고 `main`은 `298a2cb`라 항목 병합 2건과 그 사이 커밋만큼 앞서 있습니다. VPS 배포 미실시 | 이 목록은 로컬 구현·검증 상태이며 서비스 운영 완료를 뜻하지 않습니다. push·CI·배포는 별도 작업입니다 |

| 이번에 반영한 작업 | 확인 내용 |
|---|---|
| 위치 계약 | 화면 경로와 저장 위치를 분리하고 등록·이동 요청에 위치 ID 하나만 전송 |
| PATCH·상속·폐기 | 생략/null/빈 설명/가격 0 구분, 필드·버전 검증, 폐기 전환에만 확인, 응답의 null 명시 |
| 동시성·삭제 | 카테고리 병합 version 증가, 참조·위치 행 잠금, 최신 경로·권한 재확인, version 기반 삭제 |
| 잠금 후 조회 | MyBatis STATEMENT 캐시로 JDBC 잠금 이전의 SESSION 캐시 재사용 방지 |
| 조회·권한·페이지 | Item 권한 집계, 공유 카테고리 문맥, 연결 재고·등록/이동 대상 페이지, 필터 문맥 커서 |
| 변경 영향 | 제안 PATCH 기반 필드별 영향·합집합 집계, 확인한 요청 그대로 저장 |
| 편집 경험 | 원본 필드 3방향 비교, 충돌값 선택, 입력 보존, 재조회 중 편집 잠금, 이탈 확인과 관련 캐시 갱신 |
| 구조·마이그레이션 | Item 조회 책임 분리, 공통 필드 정책, 카테고리 색상 null PATCH, V25 NFKC 정규화·충돌 시 중단 |
| 계약·표시 | 승인 구현 스냅샷으로 타입 생성, 물건·재고 초안 동기화 스크립트, 일부 조회 개수의 문구 수정 |

동작 규칙·계약을 여기서 재정의하지 않습니다. [도메인](../domain.md), [공유](../sharing.md), [화면](../frontend.md), [계약](../contracts.md), [ADR-023](../decisions/ADR-023-inventory-write-consistency.md)을 따릅니다.

## 2. 검증 증거와 한계

| 검증 | 결과와 해석 |
|---|---|
| 백엔드 전체 | INV-02 항목에서 구조 원인을 확정해 고쳤습니다. 17개 통합 테스트 클래스가 application context 6개를 만들면서 context마다 Postgres·RabbitMQ 쌍을 새로 기동하던 구조를 테스트 JVM당 한 쌍 공유로 바꿨습니다. 항목 워크트리에서 46클래스·175테스트가 단일 실행으로 연속 2회 통과했고(실패 0·스킵 0) `grep -c ERROR`도 0입니다. 같은 항목에서 `frontend`·`e2e`의 기존 실패 3건도 원인을 확정해 고쳤고 `75cbaac`에서 `full` 검증을 통과했습니다(실패 0·스킵 유지). 구조 원인은 확정했고 컨테이너 유실 같은 트리거는 재현하지 못한 상태입니다. 근거·로그·남은 위험은 [PLAN-016 6.1](PLAN-016-item-workflow-and-queue.md)에 있으며 `09121d0`으로 병합했습니다 |
| 신규 핵심 회귀 | 쓰기·동시성 12개, 공유·조회·페이지 4개, PATCH 파싱 3개 통과. 전체 실행에 포함되는 부분집합이며 별도 가산하지 않음 |
| HTTP 계약 | OpenAPI 계약 테스트 2개 통과, 구현 preview 60개 path와 승인 스냅샷·타입 갱신 |
| 프론트엔드 | 최종 단위 테스트 50개, TypeScript·ESLint·Prettier·빌드 통과 |
| 브라우저·접근성 | 모킹 기반 32개 통과. 실제 API opt-in 2개는 기본 실행에서 스킵됩니다 |
| 실제 API 브라우저 | INV-01에서 시간 초과 원인을 확정해 고쳤습니다. 시간 초과는 제품 결함이 아니라 체크박스 로케이터 결함이었고(장식 `span`이 `sr-only` 입력을 덮어 `check()`가 실패), `label` 클릭·`toBeChecked()`로 바꿨습니다. 데스크톱·모바일 각 2회 모두 통과했고 Mailpit 초대·공유 `MANAGE` 후반 흐름도 포함됩니다. 근거는 [PLAN-016 6.2](PLAN-016-item-workflow-and-queue.md)이며 `298a2cb`로 병합했습니다 |
| 정적·문서·생성 | ktlintCheck·detekt·문서 검사 통과, 데이터 사전 preview 25개 table. 이벤트 preview 0채널은 기존 미완성 생성기 한계 |
| 검색 성능 | 2026-10-23 로컬 SQL·p95/p99 gate 통과 기록은 [PLAN-007](PLAN-007-search.md). 이번 변경 후 전체 성능과 VPS 동시 부하는 별도 검증 필요 |

실제 API 실행 시 독립 로컬 DB `agolock_inventory_live_20261024`에 V25까지 적용했습니다. 임시 API 프로세스는 종료했으며 테스트 DB는 남아 있습니다. `/tmp/agolock-inventory-*` 로그는 세션 임시 증거이므로 다음 작업에서 보존을 가정하지 않습니다. 최종 판단은 다시 생성한 결과와 커밋 기준으로 남깁니다.

INV-01에서 같은 DB를 쓸 수 있는 `agolock_live` 독립 DB를 만들고 8087 포트의 API로 다시 실행해 성공했습니다. 실행 조건은 [PLAN-016 6.2.2](PLAN-016-item-workflow-and-queue.md), 사용자향 절차는 [화면과 흐름 11번 항목](../frontend.md)에 있습니다.

## 3. 남은 물건·재고 개선

우선순위는 다음 실행 순서입니다. INV-01·INV-02는 완료되어 `병합`으로 표시했고, 나머지 항목은 미완료입니다. 기능 추가와 검증 공백을 구분합니다.

| ID·우선순위 | 현재 상태·수정 대상 | 완료 조건 |
|---|---|---|
| INV-01 · 높음 · 병합(`298a2cb`) | 시간 초과는 제품 결함이 아니라 테스트 로케이터 결함이었습니다. `frontend/src/shared/ui/choice.tsx:12-25`의 `sr-only` 입력을 22px 장식 `span`이 덮어 `check()`가 229회 재시도한 뒤 120초 시간 초과했고, `label` 클릭 + `toBeChecked()`로 바꿔 데스크톱 7.3초·모바일 6.6초에 통과했습니다. 실행 조건·로그는 [PLAN-016 6.2](PLAN-016-item-workflow-and-queue.md) | 모킹 없이 소유/공유 MANAGE 등록·빈 설명/0원 편집·이동이 데스크톱/모바일에서 통과 — 충족 |
| INV-02 · 높음 · 병합(`09121d0`) | 항목 워크트리에서 175개 단일 실행 연속 2회 통과. 테스트 컨테이너를 테스트 JVM당 한 쌍으로 공유하도록 수정했고, `frontend`·`e2e` 기존 실패 3건도 같은 항목에서 고쳐 `75cbaac`·`74104e8`의 `full` 검증이 통과했습니다. 기록은 [PLAN-016 6.1](PLAN-016-item-workflow-and-queue.md) | 동일 커밋의 깨끗한 환경에서 전체 백엔드·계약·프론트·브라우저 검사를 한 번에 통과하고 환경·명령·결과 보존 — 충족 |
| INV-03 · 중간 | `InventoryCommands.kt`가 여전히 값과 `*Present` Boolean을 병렬 보관. 계획의 내부 명령 타입화는 미반영 | HTTP의 생략/null 의미를 유지하면서 내부 명령을 유지/설정의 명시적 타입으로 변환. 기존 API와 회귀 테스트 보존 |
| INV-04 · 중간 | `features/inventory/api.ts`의 Item 권한·공유 필드는 선택적이며 상세 화면에 소유자 추측 fallback이 남음 | 승인 구현 스키마에 맞춰 필수 타입과 실제 호출부를 정리하고 누락 응답·권한 UI 검증 |
| INV-05 · 중간 | `InventoryResponseAssembler`의 단건 응답은 DB 조회를 포함하고 목록 응답과 effective 값 조립이 중복됨 | 조회와 순수 응답 계산을 분리하고 단건·목록의 상속/null/빈 문자열·위치 의미가 같음을 검증 |
| INV-06 · 중간 | SQL 집계·페이지 처리는 반영됐지만 변경 후 권한 조회 수·규모별 지연의 전용 측정 기록은 없음. 일부 선택기는 `collectPages`로 모든 페이지를 모음 | 재고·위치·카테고리 증가에 따른 SQL 횟수·메모리·초기 화면 지연 측정. 필요할 때만 검색/점진 조회로 개선 |
| INV-07 · 배포 전 | V25는 새 DB 적용·순수 정규화 충돌 테스트를 확인했으나 기존 데이터 충돌과 중단 후 재실행 리허설은 별도 | 기존 데이터 복사본에서 충돌 ID 보고·무변경 중단·명시적 병합 후 재실행을 검증. 자동 병합 금지 |
| INV-08 · 낮음 | 프론트 빌드의 500kB 초과 번들 경고가 남음 | 실제 로딩 측정 후 화면별 코드 분할 필요 여부 결정. 경고 임계값을 올려 해결하지 않음 |

재현 시에는 이미 떠 있는 `127.0.0.1:5173` 서버를 재사용하지 않습니다. Vite를 `VITE_API_TARGET`으로 대상 API를 고정해 직접 띄워 단독 점유하고, `VITE_API_TARGET`은 Playwright 프로세스가 아니라 Vite 프로세스에 넘깁니다(Playwright의 `webServer.command`는 이 값을 전달하지 않음). 실제 시나리오는 `INVENTORY_LIVE=1`과 SMTP/Mailpit이 필요하며, 기본 모킹 E2E의 성공을 대신 사용할 수 없습니다.

최신 API를 별도 DB·`8087` 포트에서 실행하고 가입 설정·SMTP/Mailpit이 준비된 경우의 브라우저 실행 명령입니다. 실행 전 절차와 성공 증거는 [PLAN-016 6.2.2·6.2.4](PLAN-016-item-workflow-and-queue.md), 사용자향 절차는 [화면과 흐름 11번 항목](../frontend.md)에 있습니다.

```bash
VITE_API_TARGET=http://127.0.0.1:8087 node_modules/.bin/vite --host 127.0.0.1 --port 5173   # 워크트리 frontend에서 먼저 실행
env -u CI INVENTORY_LIVE=1 MAILPIT_URL=http://127.0.0.1:8025 \
  pnpm --filter @agolock/frontend exec playwright test e2e/inventory-live.spec.ts \
  --project=desktop-chromium --workers=1 --trace on --reporter=line
```

셸에 `CI=1`이 있으면 Playwright의 `reuseExistingServer`가 꺼져 5173 점유만으로 실패하므로 `env -u CI`가 필요합니다.

성공한 뒤 `mobile-chromium`도 같은 조건으로 확인하고, 실패 시 첫 미완료 동작·요청/응답·console과 trace를 기록합니다.


## 4. 기존 출시 후속 작업

| ID·분류 | 남은 작업과 완료 조건 | 원본 계획 |
|---|---|---|
| REL-01 · 검색 | 최근 검색 30일 보관·만료 조회/정리 구현과 경계 시각·사용자 격리 검증. 현재 최근 10개 조회·전체 삭제만 존재 | [PLAN-007](PLAN-007-search.md) |
| REL-02 · 추천 | 검색 제안의 최근 추가·최근 공유 목록 연결. 현재 DTO 기본값이 빈 목록이며 홈의 최근 추가 화면은 이미 구현됨 | [PLAN-007](PLAN-007-search.md) |
| REL-03 · 공유 표시 | 계층·공유 응답의 ownerName, 내/공유 표시 규칙 결정, `/shared` 초안과 실제 계약 정합성, 화면·테스트 반영. 제안은 결정 대기 | [PLAN-013](PLAN-013-ui-review-followups.md) |
| REL-04 · 생성 계약 | 기록 모드 전환, 실제 이벤트가 0채널로 출력되는 exporter 보완, 전체 schema·계약의 코드 정합성과 호환성 검사 마감. 구현 스냅샷 타입 생성·물건/재고 부분 동기화는 완료 | [PLAN-009](PLAN-009-release-hardening.md), [계약](../contracts.md) |
| REL-05 · 이미지 | 실제 저장소·MQ를 사용한 실패·만료·재시도·중단 복구·중복 전달과 대표 이미지/접근권한 E2E. 워커 중단 복구 코드는 이미 반영됨 | [PLAN-006](PLAN-006-media-messaging.md) |
| REL-06 · 화면·접근성 | 403/404/409/offline과 이미지 상태의 시각 확인, 실기기 스크린리더·모바일 키보드·다크 상태 검증. 자동 axe와 충돌 동작 테스트 완료를 실기기 확인으로 간주하지 않음 | [PLAN-013](PLAN-013-ui-review-followups.md) |
| REL-07 · VPS staging | VPS + Compose 기준의 서버 접속·도메인·TLS·시크릿·서비스 설정, remote/CI 환경 연결과 서버 배포 단계. Dockerfile/Compose/nginx/GHCR 빌드 골격은 존재 | [PLAN-011](PLAN-011-first-release-delivery.md) |
| REL-08 · 운영 | 실제 등록·검색·이미지·공유 smoke, 복구·롤백 리허설, 모니터링·알림·동시 부하·보안·PWA·파일럿/프리즈/태그. `staging_runbook.sh`의 backup 구현은 존재하며 복구 완료 증거는 없음 | [PLAN-009](PLAN-009-release-hardening.md), [운영](../operations.md) |
| REL-09 · 통합 | INV-01의 실제 API 미검증 상태가 해소됐으므로 INV-03~08 잔여 항목 뒤 `main` 통합을 검토합니다. 원격 설정 후 push/CI와 릴리스 절차는 별도 실행 | [PLAN-014](PLAN-014-inventory-hardening.md) |

2차의 AI 인식·임베딩·이미지 유사 검색·확인 UI·비용 품질 검증은 [PLAN-008](PLAN-008-ai-image-search.md)에 남아 있습니다. 3차 확장 기능은 [운영 로드맵](../operations.md)의 별도 범위이며 1차 미완료 결함으로 분류하지 않습니다.

## 5. 다음 실행 순서

항목 단위 실행·검증·리뷰·병합 규약과 전체 대기열은 [PLAN-016](PLAN-016-item-workflow-and-queue.md)을 따릅니다. 병합 게이트가 현재 HEAD의 전체 검증 pass를 요구하므로, 이번 배치에서는 백엔드 전체 실행이 green이 되는 INV-02를 먼저 처리하고 INV-01을 이어서 실행해 둘 다 병합했습니다.

1. INV-02로 같은 커밋의 전체 검증을 확보해 병합 게이트를 green으로 만들었습니다 — `09121d0`.
2. INV-01 실제 API 시간 초과 원인을 확정하고 성공 경로를 확보했습니다 — `298a2cb`.
3. INV-03(`item/inv-03-inventory-command-types`)을 다음 항목으로 시작합니다. 의존 항목은 INV-02이며 이미 충족됐습니다. INV-03~07을 순차 개선하고 INV-08은 측정 결과로 필요 여부를 결정합니다.
4. REL-01~06의 기능·계약·이미지·UI 후속을 마감합니다. 세부 규칙은 해당 원본 문서를 갱신합니다.
5. VPS 접속·도메인·시크릿이 준비되면 REL-07~08을 실행합니다. 검증 전 출시 완료로 표시하지 않습니다.
6. REL-09에서 통합·원격 반영 상태와 검증 커밋을 갱신합니다. 지금은 push하지 않았으므로 원격 반영은 미완료입니다.

이 문서는 작업 목록의 단일 입구입니다. 새 코드 변경 후에는 해당 기능 문서와 검증 근거를 함께 갱신하고, 완료한 항목만 상태를 변경합니다.
