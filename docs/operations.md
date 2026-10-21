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

- `UPLOADING`과 `DRAFT` 이미지는 `upload_expires_at` 이후 삭제합니다.
- `FAILED` 이미지는 7일 후 삭제합니다.
- soft delete된 이미지는 30일 후 원본과 썸네일을 함께 삭제합니다.
- 만료된 공유 초대는 `EXPIRED`로 표시합니다.
- 만료된 최근 검색은 30일 후 삭제합니다.
- 만료된 `idempotency_record`는 24시간 후 삭제합니다.
- 오래된 `outbox_event`는 보관 기간 후 삭제합니다.

## 4. 배포

- 단일 모놀리식 애플리케이션으로 시작합니다.
- API와 Worker는 같은 이미지를 사용하고 실행 프로필만 분리합니다.
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

## 6. 테스트 전략

### 6.1 백엔드

- JUnit 5와 Testcontainers를 사용합니다.
- ktlint와 detekt를 CI에 포함합니다.
- PostgreSQL + pgvector 컨테이너로 통합 테스트를 수행합니다.
- RabbitMQ 컨테이너로 Outbox·DLQ·재시도를 검증합니다.
- MyBatis 매퍼는 실제 SQL로 검증합니다.
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

구현 순서, 마일스톤 의존성, 상세 작업 계획은 [구현 로드맵](implementation-roadmap.md)을 따릅니다.

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
