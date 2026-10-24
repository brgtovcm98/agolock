# 운영과 검증

> 상태: 확정
> 역할: 인프라, 배포, 보안, 테스트, 관측, 문서 검사, 로드맵의 단일 원본
> 관련 문서: [아키텍처](architecture.md), [미디어·AI](media-ai.md), [공유](sharing.md), [AGENTS.md](../AGENTS.md), [ADR-014](decisions/ADR-014-generated-docs-draft.md)

## 1. 환경

| 환경 | 용도 | 구성 |
|---|---|---|
| local | 개발 | Docker Compose |
| staging | 통합·QA | 운영과 동일 구성의 소규모 환경 |
| production | 서비스 | 관리형 PostgreSQL, S3, RabbitMQ |

### 1.1 로컬 구성

```text
app       Spring Boot + React build (Gradle·pnpm로 실행, Compose는 인프라만)
postgres  PostgreSQL + pgvector
rabbitmq  RabbitMQ + management
minio     S3 호환 스토리지
mailpit   이메일 확인
```

```bash
docker compose -f infra/docker-compose.yml up -d
```

- 로컬 실행 절차와 문제 해결은 [로컬 실행 Runbook](local-runbook.md)을 따릅니다.
- 로컬 데이터는 볼륨으로 유지합니다.
- MinIO 버킷은 시작 시 자동 생성합니다.
- 공식 `minio/minio` 이미지가 Docker Hub에서 제공되지 않아 로컬은 `bitnamilegacy/minio:2025.7.23-debian-12-r5` 고정 태그를 사용합니다. 운영 이미지가 아닙니다.
- RabbitMQ 관리 콘솔은 로컬에서만 노출합니다.
- Mailpit으로 초대·비밀번호 재설정 메일을 확인합니다.
- M0의 로컬 도구는 Java 21.0.12.1, Node 22.23.3, pnpm 10.34.7, Gradle 8.14.3입니다. `scripts/install-tools.sh`가 `.tools/`에 설치합니다.

## 2. 설정과 비밀

- 설정은 환경 변수와 프로필로 분리합니다.
- 필수 설정이 누락되면 애플리케이션이 기동 단계에서 실패합니다.
- 비밀 값은 저장소에 커밋하지 않습니다.
- 로컬은 `.env.local`, 운영은 시크릿 관리자를 사용합니다.
- 세션 쿠키는 운영에서 `Secure`를 필수로 사용합니다.
- S3 자격증명은 애플리케이션 역할에 최소 권한으로 부여합니다.
- AI provider 키는 서버에서만 사용합니다.
- presigned URL, 세션 ID, 비밀번호, 초대코드 원문을 로그에 남기지 않습니다.

### 2.1 주요 설정

| 설정 | 기본값 | 설명 |
|---|---|---|
| `auth.signup.invite-required` | staging `true`, production `false` | 가입 초대코드 요구 |
| `auth.session.timeout` | 30분 | 유휴 세션 만료 |
| `auth.session.max` | 5 | 사용자당 동시 세션 수 |
| `auth.password-reset.ttl` | 30분 | 재설정 토큰 만료 |
| `share.invitation.ttl` | 7일 | 공유 초대 만료 |
| `image.presign.ttl` | 5분 | 업로드 URL 만료 |
| `image.upload.expiry` | 24시간 | UPLOADING·DRAFT 정리 |
| `image.failed.expiry` | 7일 | FAILED 이미지 정리 |
| `image.deleted.retention` | 30일 | soft delete 후 물리 삭제 |
| `image.max-per-target` | 10 | 대상별 최대 이미지 수 |
| `search.image.timeout` | 5초 | 동기 이미지 검색 타임아웃 |
| `search.candidate-multiplier` | 5 | 벡터 검색 후보 배수 |
| `search.candidate-max` | 200 | 벡터 검색 최대 후보 |

## 3. 데이터베이스

- PostgreSQL 16 이상과 pgvector를 사용합니다.
- 스키마 변경은 Flyway 마이그레이션으로만 수행합니다.
- 마이그레이션은 애플리케이션 배포 전에 실행합니다.
- 파괴적 변경은 expand-contract 패턴으로 단계화합니다.
- 주요 테이블은 `created_at`, `updated_at`을 가집니다.
- soft delete 리소스는 `deleted_at`을 가집니다.
- Inventory는 낙관적 잠금을 위해 `version`을 가집니다.
- 생성 요청 멱등성은 `idempotency_record`로 관리합니다.
- 운영 백업은 일 1회 전체, 시간별 증분을 권장합니다.
- 복구 훈련을 분기 1회 수행합니다.
- 목표 RPO는 1시간, RTO는 4시간입니다.

### 3.1 인덱스 원칙

- 테넌트 테이블 인덱스는 `space_id`를 선두에 둡니다.
- 검색 인덱스는 `pg_trgm` GIN을 사용합니다.
- 벡터 인덱스는 HNSW cosine을 사용합니다.
- 공유 ACL 조회는 `grantee_user_id`, 리소스 ID 인덱스를 사용합니다.
- 인덱스 추가 전에 실행 계획과 쓰기 비용을 확인합니다.
- trigram GIN과 HNSW는 `space_id`를 선두에 둘 수 없으므로 예외로 합니다.
- 대신 부분 인덱스, ACL overfetch, iterative scan으로 테넌트 필터를 보완합니다.

### 3.2 정리 job

이미지 처리 중단 복구의 상태·배치·재시도 규칙은 [미디어·AI 장애 처리](media-ai.md)의 8번 항목을 따릅니다. 상태 복구와 재처리 Outbox 등록은 같은 트랜잭션으로 수행합니다.

- `UPLOADING`과 `DRAFT` 이미지는 `upload_expires_at` 이후 삭제합니다.
- `FAILED` 이미지는 7일 후 삭제합니다.
- soft delete된 이미지는 30일 후 원본과 썸네일을 함께 삭제합니다.
- 만료된 공유 초대는 `EXPIRED`로 표시합니다.
- 만료된 최근 검색은 30일 후 삭제합니다.
- 만료된 `idempotency_record`는 24시간 후 삭제합니다.
- worker는 `attempts >= 5`로 고립된 PENDING outbox를 FAILED로 회수하고, `PUBLISHED` outbox를 기본 30일, `FAILED` outbox를 기본 90일 보관 후 삭제하며(`agolock.outbox.retention.published-days`, `failed-days`), 만료된 `notification_secret`을 1시간마다 정리합니다.

### 3.3 데이터 감사·백필

- `scripts/audit_data.sql`은 orphan inventory, Outbox `PENDING`, 미인증 사용자, Share permission 분포를 읽기 전용으로 점검합니다.
- V21은 V16 이전 가입자를 grandfathering해 기존 활성 사용자의 `email_verified_at`을 `created_at`으로 백필하며(NULL 행만 갱신해 멱등), 대용량은 별도 one-off CLI 배치 UPDATE를 검토합니다.
- V22 `notification_secret`은 메일 링크 토큰을 AES-GCM으로 암호화해 outbox payload와 분리 저장합니다. outbox에는 `secretId`만 남습니다.

## 4. 배포

- 단일 모놀리식 애플리케이션으로 시작합니다.
- API와 Worker는 같은 이미지를 사용하고 실행 프로필만 분리합니다.
- Actuator 관리 포트는 9090이며 nginx에 게시하지 않습니다. 컨테이너 healthcheck만 9090을 사용합니다.
- `NOTIFICATION_SECRET_KEY`는 base64 32바이트 AES-256 키이며 staging/prod에서 필수입니다. 키를 회전하면 기존 `notification_secret`을 복호화할 수 없으므로, 회전 전에 만료된 secret을 정리하고 새 초대부터 새 키를 사용합니다.
- springdoc/Swagger는 local 프로필에서만 활성화합니다.
- CI는 GitHub Actions를 사용합니다.
- 배포 순서:
  1. 테스트와 빌드
  2. Flyway 마이그레이션
  3. 새 애플리케이션 배포
  4. 헬스 체크
  5. 스모크 테스트
- 롤백은 이전 이미지로 되돌리고, DB 마이그레이션은 하위 호환성을 유지합니다.
- 정적 프론트엔드는 CDN 또는 리버스 프록시로 제공합니다.
- API와 프론트엔드는 같은 도메인 또는 신뢰된 서브도메인으로 제공합니다.
- 쿠키는 same-origin 또는 명시적 CORS 설정을 사용합니다.

### 4.1 staging 배포 골격

- `deploy/backend.Dockerfile`은 bootJar를 빌드하고 JDK 21 JRE에서 non-root로 실행합니다.
- `deploy/frontend.Dockerfile`은 pnpm 빌드 후 nginx로 정적 파일을 서빙합니다.
- `deploy/compose.staging.yml`은 app·worker·web·PostgreSQL·RabbitMQ·MinIO·Mailpit을 묶습니다.
- API와 worker는 같은 이미지를 쓰고 `staging`·`staging,worker` 프로필로 분리합니다.
- nginx는 SPA fallback과 same-origin `/api` 프록시를 제공합니다.
- staging은 HTTPS 종단 뒤에서 `SESSION_COOKIE_SECURE=true`로 동작합니다.
- 브라우저가 presigned URL로 직접 업로드하므로 `S3_PUBLIC_ENDPOINT`는 브라우저에서 접근 가능한 주소여야 합니다.
- staging의 MinIO 9000 공개는 임시값입니다. production은 관리형 S3 + 비공개 버킷 + TLS 공개 호스트를 사용하고, MinIO 포트를 외부에 게시하지 않습니다.
- `NOTIFICATION_SECRET_KEY`는 staging/prod에서 필수이며, 시크릿 관리자에서 주입합니다. 회전 시 기존 `notification_secret`은 복호화할 수 없으므로 만료·정리 후 새 초대부터 새 키를 사용합니다.
- 이 골격은 M6 검색과 1차 프리즈 전까지 배포 완료로 간주하지 않습니다.

### 4.2 staging runbook

```bash
# 1) 필수 env와 compose 문법 확인
scripts/staging_runbook.sh preflight

# 2) 백업
scripts/staging_runbook.sh backup

# 3) Flyway V19~V23 적용(app 재배포)
scripts/staging_runbook.sh migrate

# 4) 데이터 감사와 smoke
scripts/staging_runbook.sh audit
scripts/staging_runbook.sh smoke
```

- 스크립트는 `deploy/.env.staging`을 읽습니다. 값은 저장소에 커밋하지 않습니다.
- `migrate`는 app을 재배포하므로 maintenance window와 함께 실행합니다.
- k6/EXPLAIN은 `scripts/staging_runbook.sh load`의 안내를 따릅니다.

### 4.3 Production readiness checklist

- [ ] staging DB 백업 후 Flyway V19~V23 적용, `scripts/audit_data.sql` 결과 확인
- [ ] `PUBLIC_BASE_URL`, `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `S3_SECRET_KEY`, `S3_PUBLIC_ENDPOINT`, `NOTIFICATION_SECRET_KEY`를 시크릿 관리자에서 주입
- [ ] S3는 비공개 버킷 + TLS 공개 호스트, MinIO 관리 포트는 외부 비공개
- [ ] 세션 쿠키 `Secure`, `SameSite=Lax`, CSRF, `forward-headers-strategy`를 실제 TLS 종단에 맞게 확인
- [ ] `management.server.port=9090`을 외부에 게시하지 않고 healthcheck만 사용
- [ ] `load/k6` 목록 p95 500ms, 검색 p95 800ms, 실패율 1% 미만 확인
- [ ] `scripts/explain_queries.sql`로 inventory_access/keyset/share ACL 실행 계획 확인
- [ ] Outbox `PUBLISHED` 30일 / `FAILED` 90일 retention과 DLQ 재처리 절차 확인
- [ ] 이전 이미지로 롤백 가능한지, Flyway forward-only 정책과 백업 복구 절차를 리허설
- [ ] 에러율/지연/DB 풀/Outbox PENDING/Rabbit DLQ 알림 구성

## 5. 보안

### 5.1 인증·세션

- M1은 Spring Session JDBC를 사용하고 세션 인덱스의 principal name에는 이메일이 아니라 `user_id`를 저장합니다.
- 클라이언트 IP는 기본적으로 `request.remoteAddr`을 사용합니다. 신뢰할 수 있는 프록시 뒤에서만 `FORWARD_HEADERS_STRATEGY=framework`를 설정하고, 프록시가 `X-Forwarded-For`를 덮어쓰도록 구성합니다.
- 로그아웃과 비밀번호 재설정 완료 시 해당 사용자의 모든 세션을 삭제합니다.
- 로그인 실패 10회/15분은 단일 인스턴스 메모리 카운터로 15분 잠급니다. 다중 인스턴스 전환 시 Redis로 교체합니다.
- 초대코드는 `AGO-` + Crockford Base32 20자 형식이며 정규화 후 SHA-256 해시만 저장합니다.
- 비밀번호 재설정 메일은 DB 트랜잭션 커밋 후 발송하고, 발송 실패가 202 응답과 가입·로그인을 막지 않습니다. 영속 재시도는 M5 Outbox에서 추가합니다.
- M1 계약은 `build/contracts/` 미리보기로 검증하고 `docs/generated/`의 M2~M8 설계 초안은 보존합니다.

- 비밀번호는 Argon2id로 해시합니다.
- 세션은 HttpOnly, SameSite=Lax 쿠키를 사용하고 운영에서는 Secure를 필수로 합니다.
- CSRF 토큰은 `GET /auth/csrf` 또는 `XSRF-TOKEN` 쿠키로 발급합니다.
- 상태 변경 요청은 `X-XSRF-TOKEN` 헤더를 검증합니다.
- 로그인 성공 시 세션 ID를 회전합니다.
- 로그아웃과 비밀번호 재설정 완료 시 전체 세션을 무효화합니다.
- 로그인 실패 10회/15분 시 15분 잠금을 적용합니다.
- 최대 동시 세션은 5개입니다.
- 비밀번호 재설정 토큰은 30분, 1회용입니다.
- 비밀번호 재설정 요청은 이메일 존재 여부와 무관하게 `202`를 반환합니다.
- 스테이징 초대코드는 해시 저장, 만료, 사용 횟수 제한을 적용합니다.
- 초대코드 생성·철회는 운영 CLI로 수행합니다.

### 5.2 테넌트·권한

- 모든 테넌트 데이터는 `space_id`를 가집니다.
- 복합 FK로 교차 Space 참조를 차단합니다.
- 서비스 계층에서 유효 권한을 판정합니다.
- 클라이언트가 보낸 `space_id`, 권한, 역할을 신뢰하지 않습니다.
- Item 이미지와 마스터는 OWNER, Inventory 이미지와 오버라이드는 EDIT 이상으로 분리합니다.
- 교차 Space 접근과 권한 상속을 통합 테스트로 검증합니다.
- RLS는 2차 방어선으로 추가할 수 있습니다.

### 5.3 이미지·AI

- 이미지 버킷은 비공개입니다.
- presigned URL은 5분 TTL을 사용합니다.
- 업로드 파일은 매직 바이트 검증 후 서버에서 재인코딩합니다.
- EXIF를 제거합니다.
- `UPLOADING`과 `DRAFT` 이미지는 만료 후 정리합니다.
- 목록에는 presigned URL 대신 이미지 ID를 반환합니다.
- AI provider에 전송되는 데이터를 최소화합니다.
- AI 결과는 자동 저장하지 않습니다.
- 임베딩은 모델 간 fallback을 허용하지 않습니다.
- AI 원본 응답과 presigned URL을 로그에 남기지 않습니다.

### 5.4 애플리케이션

- 입력 검증과 출력 인코딩을 적용합니다.
- SQL은 MyBatis 파라미터 바인딩을 사용합니다.
- 파일 업로드 크기·형식·개수를 제한합니다.
- 레이트리밋을 로그인, 초대, AI, 이미지 업로드, 이미지 검색에 적용합니다.
- 의존성 취약점 검사와 비밀 스캔을 CI에 포함합니다.
- 보안 헤더와 HTTPS를 강제합니다.
- 초대 토큰 링크에는 `Referrer-Policy: no-referrer`를 적용합니다.

### 5.5 레이트리밋과 메일

- 1차는 단일 인스턴스 기준 in-memory 레이트리밋을 사용합니다.
- 다중 인스턴스로 확장하면 Redis 기반으로 전환합니다.
- 기본 한도:
  - 로그인: IP와 이메일 조합으로 분당 10회, IP당 분당 30회
  - 가입 초대코드 검증: IP당 시간당 10회
  - 비밀번호 재설정 요청: 이메일당 시간당 5회, IP당 시간당 20회
  - 비밀번호 재설정 확인: IP당 분당 10회
  - 초대 생성: 사용자당 시간당 20회
  - 이미지 업로드: 사용자당 시간당 30회
  - AI 인식: 사용자당 일일 한도 설정값
  - 이미지 검색: 사용자당 분당 10회
- 운영 메일은 SMTP 또는 관리형 메일 서비스를 사용합니다.
- 초대, 초대 수락, 비밀번호 재설정 템플릿을 둡니다.
- 메일 발송 실패는 `notification` 큐에서 재시도하고 DLQ로 보냅니다.

### 5.6 헬스 체크

- liveness는 API 프로세스 응답 여부만 확인합니다.
- readiness는 DB, RabbitMQ, S3 연결을 확인합니다.
- M0의 readiness는 DB와 RabbitMQ만 확인하고, S3 health indicator는 M5 이미지 파이프라인에서 추가합니다.
- AI provider 장애는 readiness 실패로 처리하지 않습니다.
- 헬스 응답에는 비밀 값과 내부 오류 상세를 포함하지 않습니다.
- 배포 후 `bash scripts/staging_runbook.sh smoke`로 컨테이너 내부 liveness·readiness, 공개 `/healthz`·인증 설정 API의 HTTP 200, `inviteRequired`의 Boolean 타입, 비인증 `/api/v1/info`의 HTTP 401을 확인합니다. 예상 상태·응답 형식과 다르거나 연결에 실패하면 실패로 종료합니다. 실행 호스트에 Docker Compose·curl·Python 3가 필요합니다.

## 5.7 부하/실행 계획

- k6 시나리오는 [`load/k6`](../load/k6/README.md)에 있습니다. staging/로컬 전용이며 운영 계정을 사용하지 않습니다.
- GitHub Actions의 `load` workflow(`workflow_dispatch`)에서 수동 실행하며, `LOAD_TEST_EMAIL`/`LOAD_TEST_PASSWORD` 시크릿을 사용합니다.
- 목록 p95 500ms, 검색 p95 800ms, 실패율 1% 미만을 기준으로 합니다.
- 부하 테스트 전 `psql -d agolock -f scripts/explain_queries.sql`로 `inventory_access`, keyset, share ACL 실행 계획을 확인합니다.
- 결과 요약은 `build/load-*.json`에 남깁니다.

## 6. 테스트 전략

### 6.1 백엔드

- JUnit 5와 Testcontainers를 사용합니다.
- ktlint와 detekt를 CI에 포함합니다.
- PostgreSQL + pgvector 컨테이너로 통합 테스트를 수행합니다.
- RabbitMQ 컨테이너로 Outbox·DLQ·재시도를 검증합니다.
- MyBatis 매퍼는 실제 SQL로 검증합니다.
- V21 이메일 인증 백필은 별도 DB에 V15와 기존 사용자를 준비하고 V16·V20을 거쳐 적용합니다. 기존 미인증 사용자의 보정, V16 경계·이후 가입자와 삭제 사용자의 제외, 기존 인증 시각 보존을 확인합니다.
- liveness·readiness는 `ApplicationSmokeTest`, 비인증 접근은 `SecurityConfigTest`에서 확인합니다. RabbitMQ 연결은 실제 메시지 발행·DLQ 이동 통합 테스트에서 함께 확인합니다.
- 권한·테넌트 격리 테스트는 필수입니다.
- M0 공통 기반은 UUIDv7, `request_id`, 설정 검증, 로그 마스킹, problem+json, 계약 미리보기로 검증합니다.

주요 테스트:

- 같은 Space 참조 성공, 다른 Space 참조 실패
- Inventory 위치 정확히 1개 제약
- Room/Shelf/Box 삭제 차단과 soft delete 기준
- Shelf/Box 부모 이동과 공유 가시성 재계산
- 오버라이드 effective field 계산
- 마스터 수정 전파와 오버라이드 보호
- 상태 전이와 기본 검색 제외
- DISPOSED 확인 플래그 누락 시 422
- 신규 Item은 OWNER만 생성
- 공유 MANAGE는 기존 Item에만 Inventory 추가
- Item 이미지 수정은 OWNER, Inventory 이미지는 EDIT
- Room/Shelf Grant 상속과 최대 권한
- 초대 수락·거절·만료·이메일 불일치
- Share 초대·생성·철회 감사 기록
- Grant 철회 후 접근 차단
- 재고 이동 후 가시성 재평가
- 이동 이력과 같은 위치 no-op
- presign `UPLOADING` → complete → 24시간 정리
- 대표 이미지 자동 승격과 대상별 이미지 수 제한
- presigned URL 권한 확인과 배치 URL
- version 충돌과 Idempotency-Key 재생
- 검색 Item 그룹 커서와 그룹당 Inventory 제한
- 최근 검색 기록 조건과 조회 디바운스
- AI job 멱등성, 재시도, 취소, DLQ
- 임베딩 모델·버전 필터와 모델 간 fallback 금지
- 이미지 검색 5초 타임아웃
- 이벤트 routing key와 큐 바인딩
- 이미지 재인코딩과 EXIF 제거
- Category 정규화와 병합

### 6.2 프론트엔드

- Vitest와 React Testing Library를 사용합니다.
- Playwright로 주요 사용자 흐름을 검증합니다.
- 로그인, 재고 생성, 검색, 공유 초대·수락·거절, 이미지 업로드, AI 결과 확인을 포함합니다.
- 권한별 버튼 노출과 오류 상태를 검증합니다.
- 버전 충돌, 이미지 검색 타임아웃, 업로드 만료 상태를 검증합니다.
- M0에서는 E안 디자인 미리보기 3화면의 전환과 테마를 Playwright 스모크로 확인합니다.
- PWA 설치 요건과 접근성 검사를 포함합니다.

### 6.3 테스트 데이터

- 테스트마다 독립적인 Space를 생성합니다.
- 공유 테스트는 소유자와 초대 사용자를 분리합니다.
- 이미지 테스트는 작은 샘플 파일을 사용합니다.
- AI provider는 테스트에서 stub 또는 fake로 대체합니다.

### 6.4 항목 단위 작업과 검증

- 문서·설계 변경 검사는 `docs.yml`에서 실행합니다. `ci.yml`은 백엔드·프론트엔드 검증과 생성 계약 미리보기를 검사하며, 계약 생성 뒤의 문서 검사는 생성물과의 정합성 확인을 위해 유지합니다.
- 일반 기능 작업은 현재 작업 브랜치나 워크트리에서 바로 진행합니다. 전용 워크트리·`item/<slug>` 브랜치·작업 대기열 등록은 요구하지 않습니다.
- 변경에 맞는 검증을 선택하고 실행 결과를 기록합니다. 전체 검증은 여러 영역을 함께 바꾸거나 릴리스 준비를 할 때 사용하며, 모든 기능 작업의 병합 전 게이트로 요구하지 않습니다.
- 남은 작업은 [TODO](todo.md)에서 관리합니다. 기존 PLAN은 과거 실행 기록이며, 기능 작업의 시작이나 로컬 병합을 위한 필수 절차로 적용하지 않습니다. 과거 `item_flow.sh` 도구는 제거했습니다.
- push·배포 등 외부 시스템이나 사용자에게 영향을 주는 반영은 실행 전에 사용자 확인을 받습니다.

### 6.5 테스트 컨테이너 수명과 실행 환경

- 통합 테스트는 클래스 속성·프로필 조합에 따라 Spring application context를 여러 개 만들지만, Postgres·RabbitMQ 컨테이너는 테스트 JVM 하나가 한 쌍만 사용합니다(`backend/src/test/kotlin/com/agolock/support/TestcontainersConfiguration.kt`의 `SharedTestcontainers`). 컨테이너를 context 스코프 빈으로 선언하면 context마다 한 쌍이 새로 기동해 기동 지점이 context 수만큼 늘어납니다.
- 컨테이너를 `@Bean @ServiceConnection`(`Startable`)으로 등록하지 않고 `JdbcConnectionDetails`·`RabbitConnectionDetails` 빈으로 연결 정보만 노출합니다. Spring은 context 종료 시 `Startable` 빈을 닫으므로, 컨테이너를 빈으로 두면 첫 context 정리에서 공유 컨테이너가 내려가 종료 시점에 `Unexpected error occurred in scheduled task` 오류가 남고 `@DirtiesContext`가 실행 중 컨테이너를 종료시키는 경로가 열립니다.
- context 사이의 스키마 간섭을 막기 위해 context마다 빈 데이터베이스 `test_<uuid>`를 하나 만듭니다. 한 스키마를 공유하면 `@Scheduled` Outbox relay가 다른 context의 `PENDING` 이벤트를 가져가 그 context의 메일 fake로 발송해, 초대 메일을 기다리는 테스트가 실패할 수 있습니다. 이 데이터베이스는 명시적으로 삭제하지 않으며 컨테이너와 함께 사라집니다.
- 컨테이너는 마지막 테스트 뒤 JVM 종료와 함께 내려가고 Ryuk이 20~35초 뒤 정리합니다. 실행 직후가 아니라 35초 뒤 `docker ps -a`로 남은 컨테이너가 없는지 확인합니다.
- 컨테이너 수 회귀 감지는 `docker events --since <시작 epoch> --until <종료 epoch> --filter type=container --filter event=create --format '{{.Actor.Attributes.image}}' | sort | uniq -c`로 합니다. 정상값은 pgvector 1, RabbitMQ 1, MinIO 1, Ryuk 1의 합계 4이며, 늘어나면 테스트 JVM당 한 쌍 공유가 깨진 것입니다.
- 테스트 결과 판정은 `BUILD SUCCESSFUL`과 `backend/build/test-results/test/TEST-*.xml`의 `tests`·`failures`·`errors`·`skipped` 합계로 합니다. 종료 로그에 남는 일부 ERROR만으로 실패로 판정하지 않습니다. `:backend:test`는 이전 결과가 남아 있으면 `UP-TO-DATE`로 끝나 테스트를 실행하지 않으므로, 실행 증거가 필요하면 `:backend:cleanTest`를 함께 실행합니다.
- 이 개발 환경에서 `compileKotlin`·`compileTestKotlin`이 Kotlin 컴파일 데몬의 RMI 연결에서 수 분 멈추는 경우가 있습니다. 그때는 `-Pkotlin.compiler.execution.strategy=in-process`를 붙여 컴파일하며, 테스트 동작은 바뀌지 않습니다.
- Gradle 실행 전 `source scripts/env.sh`로 `GRADLE_USER_HOME`·`NPM_CONFIG_CACHE`·`PYTHONPATH`·`PLAYWRIGHT_BROWSERS_PATH`를 메인 체크아웃 기준으로 고정하고 JDK 21 `JAVA_HOME`을 사용합니다. 기본 `java`가 21이 아니면 Gradle 실행이 실패합니다.

## 7. 관측

- 구조화 로그에 `request_id`, `space_id`, `user_id`, `job_id`를 포함합니다.
- 메트릭:
  - API 요청 수·지연·오류율
  - DB 연결과 슬로우 쿼리
  - RabbitMQ 큐 길이·DLQ 수
  - Outbox 미발행·실패 수
  - AI 호출 지연·실패율·비용
  - 이미지 처리 시간, 업로드 만료 정리 수
  - 이미지 검색 타임아웃 비율
  - 검색 지연과 결과 없음 비율
- Sentry로 예외를 수집합니다.
- 알림 기준:
  - 5xx 오류율 급증
  - DLQ 메시지 증가
  - Outbox 적체
  - AI 비용 한도 초과
  - 디스크·DB 용량 임계치
  - 백업 실패

## 8. 문서 운영

- 문서 인덱스는 `docs/index.yaml`입니다.
- 코드 변경 후 `scripts/docs_impact.py`로 영향 문서를 확인합니다.
- 문서 검사는 `scripts/docs_check.py`로 수행합니다.
- 설계 단계에서는 [ADR-014](decisions/ADR-014-generated-docs-draft.md)에 따라 generated 초안을 수동 갱신합니다.
- M0에서는 [ADR-015](decisions/ADR-015-contract-preview-mode.md)에 따라 계약 생성 결과를 `build/contracts/`에만 기록하고 `docs/generated/` 초안을 보존합니다.
- 구현 착수 시 springdoc OpenAPI, 스키마 introspection, 이벤트 export 생성기를 도입합니다.
- 생성기 도입 후 generated 문서는 직접 수정하지 않습니다.
- 문서 링크, YAML, OpenAPI, 이벤트 스키마를 검사합니다.
- `docs_check.py`는 설계 초안과 미리보기의 operationId·경로 파라미터·`$ref`·머리말을 검사합니다.
- API 변경 시 프론트엔드 API 타입을 재생성하고 하위 호환성 검사를 CI에 포함합니다.
- CI의 `docs` 워크플로가 `scripts/docs_check.py`를 실행하고, `ci` 워크플로의 contract job이 미리보기 생성을 검증합니다.
- ADR 상태가 `Superseded`이면 대체 ADR을 링크합니다.
- 문서 수가 늘어나면 5페이지 초과 여부와 변경 주기를 기준으로 분할합니다.

## 9. 로드맵

### 1차: 핵심 도메인

- 인증, Space, 초대코드
- 계층, Item, Inventory, 오버라이드
- 소유자만 신규 Item 생성, 공유 MANAGE는 기존 Item에 Inventory 추가
- 상태, 카테고리, 삭제, 이동, 이동 이력
- 키워드 검색, 최근 검색, 자주 본 물건, 검색 제안
- 초대 전용 공유, 수락, 거절, 권한
- 이미지 업로드와 수동 입력
- 최소 감사 기록
- 문서 검사와 기본 운영

종료 조건:

- 핵심 CRUD와 권한 테스트 통과
- 교차 테넌트 접근 차단 검증
- 이미지 업로드·다운로드 권한 검증
- 공유 초대 수락 전 접근 차단 검증

### 2차: AI와 이미지 검색

- AI 인식 job
- 이미지 임베딩과 유사도 검색
- 동기 이미지 검색 타임아웃과 오류 처리
- Outbox, RabbitMQ, DLQ
- AI 결과 확인 UI
- 비용·품질 벤치마크

종료 조건:

- AI 실패가 재고 저장을 막지 않음
- job 멱등성과 재처리 검증
- 이미지 검색 ACL 필터 검증
- 임베딩 모델·버전 필터 검증

### 3차: 확장

- 선반별 비공개 예외
- 그룹·링크 공유
- 직접 공유 재검토
- 인앱 알림과 알림 센터
- 사진별 임베딩
- Space 간 복사
- 감사 로그 UI와 데이터 내보내기
- OAuth

현재 할 일과 개별 작업의 기술 의존성은 [TODO](todo.md)를 참고합니다. [기존 구현 로드맵](implementation-roadmap.md)은 초기 마일스톤 검토 기록으로 보관합니다. 제품 단계별 출시 조건은 위 종료 조건을 따릅니다.

## 10. 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 공유 권한 누락 | 데이터 노출 | ACL 통합 테스트, 기본 거부 |
| 공유 사용자의 잘못된 마스터 생성 | 데이터 품질 | 신규 Item 소유자 제한 |
| 벡터 검색 품질 저하 | 검색 실패 | 샘플 벤치마크, 임계값 보정 |
| 임베딩 모델 혼합 | 검색 무효 | 모델·버전 필터, fallback 금지 |
| AI 비용 증가 | 운영 비용 | 캐시, 한도, provider 비교 |
| 이미지 저장소 비용 | 비용 증가 | 썸네일, 수명주기, 압축, 만료 정리 |
| 업로드 고아 객체 | 저장소 낭비 | UPLOADING·DRAFT 정리 job |
| 큐 적체 | 처리 지연 | DLQ 알림, worker 확장 |
| 문서와 코드 불일치 | 유지보수 비용 | index.yaml, 생성 문서, 검사 스크립트 |
| 마이그레이션 실패 | 서비스 중단 | expand-contract, 백업, 롤백 훈련 |

## 11. 요구사항 추적

개별 요구사항 ID의 단일 원본은 [제품 사양](product-spec.md)의 요구사항 표입니다. 이 표는 그룹 단위 추적만 제공합니다.

| 요구사항 그룹 | 설계 문서 | 테스트 |
|---|---|---|
| REQ-TEN | [아키텍처](architecture.md), [데이터 사전](generated/data-dictionary.md) | 교차 Space 접근 차단 |
| REQ-LOC | [도메인](domain.md) | 위치 CHECK, 삭제 차단, 이동, 계층 이동 |
| REQ-ITEM | [도메인](domain.md), [공유](sharing.md) | effective field, 마스터 전파, 신규 Item 권한 |
| REQ-STAT | [도메인](domain.md), [검색](search.md) | 상태 전이, 폐기 제외, 확인 플래그 |
| REQ-CAT | [도메인](domain.md), [검색](search.md) | effective category, 정규화, 병합 |
| REQ-SHR | [공유](sharing.md) | 상속, 최대 권한, 초대, 철회 |
| REQ-SRH | [검색](search.md) | 범위, 그룹핑, 커서, 추천, 이미지 검색 |
| REQ-IMG | [미디어·AI](media-ai.md) | 업로드 상태, 권한, 재인코딩, 대표 이미지 |
| REQ-AI | [미디어·AI](media-ai.md) | 멱등성, 재시도, 확인 저장, 모델 버전 |
| REQ-OPS | [AGENTS.md](../AGENTS.md), 이 문서 | 문서 검사 |

- 로컬 MinIO는 `MINIO_API_CORS_ALLOW_ORIGIN`으로 Vite 개발 서버 origin만 허용합니다. 운영 CORS는 배포 환경에서 별도로 제한합니다.

## DLQ 재처리

`image.process.dlq`의 메시지는 RabbitMQ Management API로 조회해 `agolock.events`/`image.uploaded`로 재발행할 수 있습니다.

```bash
# 건수 확인만
python3 scripts/dlq_replay.py --dry-run --limit 10

# 실제 재발행
RABBITMQ_MANAGEMENT_URL=http://localhost:15672 \
RABBITMQ_USER=agolock \
RABBITMQ_PASSWORD=agolock-local-secret \
python3 scripts/dlq_replay.py --limit 10
```

- 기본값은 `--dry-run`과 동일하게 안전하게 건수만 확인합니다. 재발행은 `--dry-run`을 생략해야 합니다.
- 재발행은 at-least-once이므로 worker의 `imageId` 상태 멱등성에 의존합니다.
