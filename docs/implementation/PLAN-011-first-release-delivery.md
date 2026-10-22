# PLAN-011: 1차 릴리스 완료와 배포 구성

> 상태: 실행 계획 초안 (2026-10-22), 인프라 결정 승인 대기
> 역할: 1차(M0~M6) 마감, staging 배포, production gate의 실행 순서·산출물·검증
> 상위 문서: [구현 로드맵](../implementation-roadmap.md), [운영](../operations.md)
> 관련 문서: [M5 계획](PLAN-006-media-messaging.md), [M6 계획](PLAN-007-search.md), [M8 계획](PLAN-009-release-hardening.md), [등록 UX P0/P1](PLAN-010-registration-ux.md), [화면과 흐름](../frontend.md), [아키텍처](../architecture.md)
> 전제: M0~M4 완료, M5 진행 중, M6 미착수, M7은 2차, M8은 착수 전

## 1. 목표와 비목표

1차 제품을 “등록·관리·공유·검색이 실제 인프라에서 동작하는 상태”로 닫고, 같은 구성으로 staging에 배포한 뒤 production gate를 통과합니다.

| 구분 | 내용 |
|---|---|
| 목표 | M5.5 close-out, M6 검색, 1차 프리즈, staging 배포, M8 최소 경화, production 승인 |
| 포함 | 이미지 수명주기 보완, 검색 API·UI, 릴리스 계약, Docker/Compose/nginx, CI/CD, 백업·롤백·관측 |
| 제외 | M7 AI·이미지 검색, 3차 기능, 마이크로서비스, Kubernetes, 읽기 모델 선제 도입 |
| 성공 기준 | 1차 체크리스트 통과, staging 스모크·복구·롤백 통과, 알려진 P0 0건 |

## 2. 기준선과 현재 격차

| 영역 | 현재 | 1차 완료까지 |
|---|---|---|
| M0~M4 | 완료 | 유지 |
| M5 | presign·complete·목록·URL·대표·삭제·Outbox·worker 착수 | M5.5 P0: Item 권한, S3 health, worker 프로필, Item 갤러리, 업로드 상태 UI |
| M6 | 없음. `/items?q=` 임시 이름 검색만 존재 | `/search`·추천·그룹 커서·필터·정렬·검색 UI |
| P0/P1 UX | 등록 시트·상세·편집·공유·공유 위치·Box 이동 완료 | 1차 프리즈 전 문서·잔여물 정리 |
| 품질 | backend full test, frontend 34개, E2E 10개 green | 검색·이미지 E2E, staging 스모크 |
| 배포 | Dockerfile·prod 프로필·nginx·릴리스 workflow 없음 | staging/production 동일 스크립트 |
| 운영 | 백업·관측·롤백 리허설 없음 | M8 최소 gate |

현재 최신 마이그레이션은 `V16__email_verification.sql`입니다. M6 계획의 `V16`·`V17` 표기는 오래되었으므로 **V17·V18로 재확정**합니다.

## 3. 릴리스 계약

### 3.1 1차 포함·제외

| 포함 | 제외 |
|---|---|
| 인증·Space·초대코드 | M7 AI 인식·임베딩·이미지 검색 |
| 계층·Item·Inventory·오버라이드·상태·이동 이력 | 수량·대여·보증·다중 통화 |
| 초대 전용 공유·MANAGE 재고 추가 | 그룹·링크·직접 공유 |
| 이미지 업로드·갤러리·대표 이미지 | DRAFT 저장 전 연결·AI 제안 |
| 키워드 검색·필터·정렬·최근 검색·자주 본 물건·제안 | Meilisearch·한국어 형태소 분석 |
| PWA 설치 요건(최소) | 오프라인 편집·실시간 동기화 |

### 3.2 프리즈와 버전

- `v0.1.0-alpha`: staging 배포
- `v0.1.0-beta`: 5~10명 파일럿
- `v0.1.0`: 1차 production
- 1차 프리즈 이후에는 P0 버그·운영 수정만 허용하고, 새 기능은 `v0.2.0` 이후로 보냅니다.

### 3.3 완료 정의

- 1차 범위의 기능이 모두 동작합니다.
- M5.5 P0 0건, M6 p95 300ms 이하 또는 개선안 확정.
- 검색 ACL parity, 테넌트 격리, 이미지 권한, 공유 수락 전 차단 테스트 통과.
- 프론트 format·lint·test·build, 백엔드 ktlint·detekt·test, E2E, 계약·문서 검사 통과.
- staging에서 실제 PostgreSQL·S3·RabbitMQ·SMTP로 스모크 통과.
- 백업·복구·롤백 리허설 통과, 알려진 P0 0건.

### 3.4 제품 지표

| 지표 | 목표 |
|---|---|
| 첫 물건 등록 완료율 | 70% 이상 |
| 등록 소요 시간 | 중앙값 60초 이하 |
| 검색 결과 클릭률 | 40% 이상 |
| 검색 성공률(결과 있음) | 60% 이상 |
| 7일 재방문율 | 25% 이상 |
| 공유 초대 수락률 | 50% 이상 |
| 검색 p95 | 300ms 이하 |

## 4. 작업 흐름

### 4.0 릴리스 통제 (0.5~1일)

| 작업 | 내용 | 산출물 |
|---|---|---|
| R-1 | 1차 범위·제외·프리즈·지표 확정 | 이 문서 승인 |
| R-2 | main 브랜치 보호, PR 필수, CI required check | 저장소 설정 |
| R-3 | 이슈 라벨 `P0`·`P1`·`P2`, 릴리스 책임자 지정 | 운영 규칙 |
| R-4 | 릴리스 브랜치·태그 규칙 | `release/v0.1.0`, 태그 |

### 4.1 M5.5 close-out (1~2일)

| ID | 작업 | 주요 파일 | 완료 기준 |
|---|---|---|---|
| M5-1 | Item 이미지 권한을 `OWNER`, Inventory를 `EDIT`로 분리 | `media/service/ImageService.kt`, `ImageAccessTest` | 권한 matrix 통과 |
| M5-2 | S3 health indicator와 readiness `s3` 추가 | `media/storage/S3HealthIndicator.kt`, `application.yml` | S3 중단 시 readiness DOWN |
| M5-3 | `worker` 프로필 분리, API는 listener 비활성 | `application-worker.yml`, `ImageProcessConsumer.kt`, `ImageCleanupJob.kt` | API·worker 분리 기동 |
| M5-4 | Item 이미지 갤러리와 대상별 이미지 컴포넌트 | `frontend/src/features/media/*`, `item-detail-page.tsx` | Item 이미지 업로드·대표·삭제 |
| M5-5 | 업로드 상태 머신·WebP 변환·용량/형식 오류·만료·재시도 | `InventoryImages` 공통화, `image-upload.ts` | 실패·만료·재시도 E2E |
| M5-6 | presign/complete/READY/대표 승격 E2E | `e2e/media.spec.ts` | staging 전 green |

### 4.2 M6.0 검색 de-risk (2~3일)

| ID | 작업 | 주요 파일 | 완료 기준 |
|---|---|---|---|
| M6-1 | `GET /search`, `/search/recent`, `/search/frequent`, `/search/suggestions`, `POST /items/{itemId}/view` 계약 고정 | `docs/generated/openapi.yaml`, `frontend/src/shared/api/schema.d.ts` | 계약 검사·타입 생성 |
| M6-2 | V17 검색 인덱스, V18 추천 테이블 | `V17__search_indexes.sql`, `V18__search_recommendations.sql` | Flyway·인덱스 테스트 |
| M6-3 | 10k Item·50k Inventory 시드 | `scripts/perf/seed.sql` | 재현 가능한 시드 |
| M6-4 | `AccessService` 접근 가능 Room/Shelf ID와 ACL parity 초안 | `hierarchy/service/AccessService.kt`, `SearchAclParityTest` | 검색 결과와 권한 일치 |
| M6-5 | 그룹 페이지 CTE·`EXPLAIN`·p95 측정 | `search/persistence/SearchQueryMapper.kt` | 300ms 이하 또는 개선안 |
| M6-6 | 읽기 모델 필요 여부 결정 | ADR 후보 | 필요 시 ADR, 아니면 종료 |

**Gate:** M6.0을 통과하지 못하면 검색 UI를 시작하지 않습니다. 인덱스·후보 축소로 해결되지 않을 때만 읽기 모델을 별도 ADR로 검토합니다.

### 4.3 M6 backend (3~4일)

| ID | 작업 | 주요 파일 | 완료 기준 |
|---|---|---|---|
| M6-7 | 검색 모듈·컨트롤러·서비스·DTO | `search/api`, `search/service`, `search/domain` | OpenAPI 일치 |
| M6-8 | 그룹 커서·그룹당 Inventory 5개·`hasMoreInventories` | `SearchQueryMapper.xml`, `SearchAssembler` | 커서 중복·누락 없음 |
| M6-9 | effective field·위치·카테고리·상태·공유·사진 필터 | `SearchQueryMapper.xml` | 필터 matrix 통과 |
| M6-10 | 정렬 5종, `DISPOSED` 기본 제외 | `Sort` 검증, 쿼리 | 정렬·폐기 테스트 |
| M6-11 | 최근 검색·자주 본 물건·제안·조회 디바운스 | `RecentSearchService`, `ViewService` | 30분 디바운스·30일 정리 |
| M6-12 | ACL parity·테넌트·공유 철회 테스트 | `SearchAclParityTest` 등 | P0 0건 |

### 4.4 M6 frontend (2~3일)

| ID | 작업 | 주요 파일 | 완료 기준 |
|---|---|---|---|
| M6-13 | 검색 API·쿼리 키 | `features/search/api.ts` | 타입·캐시 일관성 |
| M6-14 | `/search` 결과 그룹 카드·더 보기 | `search-page.tsx`, `search-result-group.tsx` | 그룹 펼침·5개 제한 |
| M6-15 | 필터·정렬 칩, 최근 검색·자주 본 물건 | `search-filters.tsx`, `recent-searches.tsx` | 모바일·데스크톱 |
| M6-16 | 홈 검색 진입을 `/search`로 교체 | `home-page.tsx`, `search-field` | 임시 `q` 검색 제거 |
| M6-17 | 로딩·빈 상태·오류·재시도·접근성 | 공통 상태 | axe critical 0 |
| M6-18 | 검색 E2E | `e2e/search.spec.ts` | 결과·필터·커서 |

### 4.5 1차 프리즈 (1~2일)

| ID | 작업 | 완료 기준 |
|---|---|---|
| F-1 | Box 이동 UI | Box를 다른 Shelf로 이동 |
| F-2 | Item 이미지 갤러리 최종 연결 | Item 상세·수정에서 확인 |
| F-3 | README·frontend.md·PLAN 상태 갱신 | 코드와 문서 일치 |
| F-4 | `.tmp-ux/` 등 추적 잔여물 정리 | 저장소 위생 |
| F-5 | 전체 CI·E2E·문서 검사 | `v0.1.0-rc.1` |
| F-6 | P0/P1 이슈 정리, 프리즈 공지 | P0 0건 |

### 4.6 Staging 배포 (2~3일)

| ID | 작업 | 주요 파일 | 완료 기준 |
|---|---|---|---|
| D-1 | backend/frontend Dockerfile | `deploy/backend.Dockerfile`, `deploy/frontend.Dockerfile` | 재현 가능한 이미지 |
| D-2 | nginx same-origin·SPA·보안 헤더 | `deploy/nginx/default.conf` | `/api` 프록시 |
| D-3 | staging/prod 프로필 | `application-staging.yml`, `application-prod.yml`, `application-worker.yml` | Secure 쿠키·Swagger off |
| D-4 | Compose·env template | `deploy/compose.staging.yml`, `.env.staging.example` | clean VM 기동 |
| D-5 | GHCR 이미지·release workflow | `.github/workflows/release.yml` | staging 자동 배포 |
| D-6 | healthcheck·smoke·rollback 스크립트 | `scripts/ops/*.sh` | staging 통과 |
| D-7 | 실제 인프라 스모크 | staging | 등록·검색·이미지·공유 |

### 4.7 Production gate·M8 최소 (3~5일)

| ID | 작업 | 기준 |
|---|---|---|
| O-1 | DB 백업·복구 훈련 | RPO 1시간·RTO 4시간 |
| O-2 | 롤백 리허설 | 이전 태그 5분 내 복구 |
| O-3 | 관측·알림 | 5xx·p95·DLQ·Outbox |
| O-4 | 부하·슬로우 쿼리 | 검색 p95, 5xx |
| O-5 | 보안 스캔 | gitleaks·Trivy·의존성 |
| O-6 | 접근성·PWA | axe critical 0, 설치 요건 |
| O-7 | 런북·온콜 | 배포·장애·복구 |
| O-8 | production 승인 | `v0.1.0` |

## 5. 일정과 병렬성

1인 기준 **3~4주**를 권장합니다.

| 주 | 작업 |
|---|---|
| 1주 | 릴리스 통제, M5.5, M6.0 |
| 2주 | M6 backend, M6 frontend 착수 |
| 3주 | M6 frontend, 1차 프리즈, staging |
| 4주 | staging 파일럿, M8 최소, production gate |

- M5.5와 M6.0은 순차, M6 backend와 frontend는 계약 고정 후 병렬 가능합니다.
- 배포 골격(Dockerfile·프로필)은 M6와 병렬로 준비해도 되지만, staging 배포는 1차 프리즈 후에 합니다.
- 프론트엔드와 백엔드를 다른 작업자가 맡으면 2주 차부터 병렬로 진행합니다.

## 6. 배포 구성

### 6.1 권장 토폴로지

```text
[Browser] → HTTPS → [nginx]
                       ├── /        → frontend dist
                       └── /api     → [app: Spring Boot API]
                                      ├── PostgreSQL + pgvector
                                      ├── S3
                                      ├── RabbitMQ
                                      └── SMTP
[worker: 같은 이미지, worker 프로필, 포트 없음]
```

- 첫 배포는 단일 VM + Docker Compose + 관리형 PostgreSQL/S3/RabbitMQ/SMTP를 권장합니다.
- staging은 같은 Compose 구성을 쓰고, production은 관리형 서비스로 교체합니다.
- API와 worker는 같은 이미지, 다른 프로필입니다.
- nginx가 정적 파일과 API를 같은 origin으로 제공해 CORS를 열지 않습니다.

### 6.2 설정 원칙

- `APP_ENVIRONMENT=staging|production`
- `PUBLIC_BASE_URL=https://...`
- `SESSION_COOKIE_SECURE=true`
- `FORWARD_HEADERS_STRATEGY=framework`
- `SPRING_PROFILES_ACTIVE=staging` (API), `staging,worker` (worker)
- Swagger/API docs는 staging·production에서 비활성화
- Flyway는 API에서만 실행, worker는 `flyway.enabled=false`
- readiness는 `db,rabbit,s3`
- 시크릿은 GitHub Environment/서버 시크릿으로만 주입
- 이미지 태그는 `sha`와 릴리스 태그를 함께 사용

### 6.3 CI/CD

| 단계 | 내용 |
|---|---|
| PR | 기존 CI + 검색·이미지 E2E, 계약·문서 |
| release build | backend/frontend 이미지, GHCR push, SBOM |
| staging | 자동 배포 → health → smoke |
| production | staging 15분 관측 후 수동 승인 → 동일 스크립트 |
| rollback | 이전 이미지 태그, 다운 마이그레이션 금지 |

### 6.4 배포 순서

1. 변경 목록·CI·태그 확인
2. DB 백업
3. Flyway 마이그레이션
4. API 배포·readiness
5. worker 배포
6. health·smoke
7. 15분 관측
8. production 승인 또는 롤백

## 7. 검증 매트릭스

| 영역 | PR | staging | production |
|---|---|---|---|
| 문서·계약 | docs_check, OpenAPI diff | 동일 | 동일 |
| 백엔드 | ktlint·detekt·test | 반복 | 동일 |
| 프론트 | format·lint·test·build | 동일 | 동일 |
| E2E | 핵심 8흐름 | 전체 | smoke |
| 검색 | SearchAclParity·커서·필터 | p95 | p95 |
| 이미지 | 권한·상태·S3 health | worker READY | 동일 |
| 보안 | gitleaks·audit | Trivy image | SBOM·서명 |
| 복구 | 없음 | 백업 복구 | 롤백 리허설 |
| 접근성 | axe 핵심 | 전체 | 수동 |

## 8. 리스크와 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| M6 p95 미달 | 검색 UI 지연·일정 | M6.0 gate, 인덱스·후보 축소, 읽기 모델은 마지막 |
| 검색 ACL 불일치 | 권한 유출 | SearchAclParityTest, 철회 즉시 반영 |
| M5 worker/S3 | 이미지 미노출 | worker 프로필, S3 health, E2E |
| 배포 설정 누락 | 기동 실패 | staging에서 production과 동일 구성 |
| 마이그레이션 충돌 | 배포 중단 | V17/V18 재확정, expand-contract |
| 범위 팽창 | 1차 지연 | 프리즈, M7 보류 |
| 병렬 작업 충돌 | 재작업 | 파일 소유권·PR·짧은 브랜치 |
| 백업 미검증 | 데이터 손실 | 복구 훈련을 gate로 |

## 9. 결정이 필요한 항목

| 항목 | 권장안 | 대안 |
|---|---|---|
| 인프라 | 단일 VM + Docker Compose | Kubernetes/PaaS |
| 데이터 서비스 | production 관리형, staging Compose | 전부 관리형 |
| 레지스트리 | GHCR | 다른 registry |
| 도메인·TLS | nginx + Let’s Encrypt | 클라우드 LB |
| M6 범위 | 1차 전체 검색 | 파일럿에서 검색 제외 |
| M7 | 2차로 유지 | 1차 포함 |
| Box 이동 UI | 1차 포함 | 2차 |
| PWA | 설치 요건만 1차 | 오프라인까지 1차 |

## 10. 체크리스트

### 1차

- [ ] M5.5 P0 0건
- [ ] M6.0 gate 통과
- [ ] 검색 API·UI·E2E
- [ ] 이미지 Item/Inventory 권한
- [ ] 1차 프리즈·문서·P0 0건
- [ ] `v0.1.0-rc.1`

### Staging

- [ ] Dockerfile·Compose·nginx
- [ ] staging 프로필·시크릿
- [ ] Flyway·health·smoke
- [ ] 검색·이미지·공유 스모크
- [ ] `v0.1.0-alpha`

### Production

- [ ] 백업·복구 훈련
- [ ] 롤백 리허설
- [ ] 관측·알림
- [ ] 보안·부하·접근성
- [ ] `v0.1.0`


## 11. 진행 기록

| 날짜 | 완료 | 검증 |
|---|---|---|
| 2026-10-22 | M5-1 Item 이미지 OWNER 권한, M5-2 S3 health, M5-3 worker 프로필, M5-4 Item 갤러리, M5-5 업로드 상태·취소·재시도 | backend 이미지 권한·S3 health 테스트, frontend lint·34 tests, E2E 10개 |
| 2026-10-22 | 배포 골격: backend/frontend Dockerfile, nginx, staging Compose, staging/prod 프로필, GHCR release workflow, healthcheck·smoke | compose config, 두 이미지 빌드, staging 스택 기동·readiness·worker 분리 확인 |
| 2026-10-22 | M6 MVP: V17/V18, 검색 API·ACL·최근 검색·자주 본 물건·조회 기록, `/search` UI, 홈 검색 연결 | 검색 통합 테스트, 프론트 35 tests, E2E 12개 |
| 2026-10-22 | Box 이동 UI와 이동 테스트 | 프론트 단위 테스트 |
