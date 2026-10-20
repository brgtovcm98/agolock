# PLAN-002: M1 Identity·Space 상세 구현 계획

> 상태: M1 구현 완료
> 역할: 구현 로드맵 M1의 가입·로그인·세션·CSRF·초대코드·비밀번호 재설정·Space 설정·메일·레이트리밋·화면 구현 계획
> 상위 문서: [구현 로드맵](../implementation-roadmap.md)
> 선행 문서: [PLAN-001 기반 부트스트랩](PLAN-001-foundation.md), [PLAN-001 계약·CI·검증](PLAN-001-foundation-delivery.md)
> 관련 문서: [제품 사양](../product-spec.md), [아키텍처](../architecture.md), [운영](../operations.md), [계약](../contracts.md), [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md), [데이터 사전](../generated/data-dictionary.md), [OpenAPI](../generated/openapi.yaml), [ADR-006](../decisions/ADR-006-session-auth.md), [ADR-014](../decisions/ADR-014-generated-docs-draft.md), [ADR-015](../decisions/ADR-015-contract-preview-mode.md), [ADR-016](../decisions/ADR-016-neo-brutal-design-system.md)


## 0. 확정된 결정

2026-10-21에 다음 항목을 확정했습니다.

| 항목 | 결정 |
|---|---|
| 계약 기록 | M1은 `build/contracts/` 미리보기 유지. `docs/generated/`의 M2~M8 설계 초안은 보존하고 기록 모드 전환은 M4 또는 M8에서 판단 |
| 로그아웃 | 해당 사용자의 모든 Spring Session 무효화 |
| 로그인 잠금 | 단일 인스턴스 메모리 실패 카운터(10회/15분, 15분 잠금) |
| 초대·재설정 오류 | 초대코드 무효와 재설정 토큰 무효는 `422 Unprocessable Entity` |
| 레이트리밋 | 로그인 10회/분(IP 30회/분), 가입 10회/시간, 재설정 요청 5회/시간(IP 20회/시간), 재설정 확인 10회/분 |
| 메일 | 트랜잭션 커밋 후 동기 발송, 실패는 로그만 남기고 202 유지. Outbox 재시도는 M5 |
| 동시 세션 | 최대 5개, 초과 시 가장 오래된 세션 만료 |
| 오류 색 | `--danger` 역할 토큰 추가 |
| 다음 단계 | M1 완료 후 M2 계층·권한 구현으로 진행 |

## 1. 목적과 범위

M1은 이메일·비밀번호 가입, 서버 세션 로그인, 초대코드, 비밀번호 재설정, 개인 Space와 표시 이름 관리를 완성하는 마일스톤입니다. 이 문서는 다음 세션에서 바로 구현할 수 있도록 파일, 마이그레이션, API, 화면, 테스트, 완료 조건을 고정합니다.

| 포함 | 비목표 |
|---|---|
| `app_user`, `space`, `invite_code`, `invite_code_use`, `password_reset_token`, `user_status` | OAuth, `user_identity`, 매직 링크, MFA, remember-me |
| Spring Session JDBC, `SESSION` 쿠키, 세션 회전, 동시 세션 제한 | 이메일 변경, 계정 삭제 API, 데이터 내보내기 |
| `/auth/config`, `/auth/csrf`, 가입·로그인·로그아웃·내 정보·표시 이름 수정 | 공유 초대·Grant(M4), 카테고리 관리(M3), 감사 로그(M4) |
| 초대코드 CLI, 가입 시 초대코드 소비 | `idempotency_record`와 `Idempotency-Key`(M1 비목표, M2에서 필요 시 추가) |
| 비밀번호 재설정, 메일 포트·템플릿, 레이트리밋 | `notification` 큐·Outbox·DLQ(M5) |
| Space 이름 수정, SCR-001·SCR-002·SCR-017 | 전체 스택 Playwright E2E·PWA(M8), 3차 범위 |

## 2. 선행 조건과 M0 의존성

M0는 커밋 `49e6a1a` 기준으로 완료되어 있습니다. 착수 전에 `docker compose -f infra/docker-compose.yml up -d`, `./gradlew :backend:test`, `pnpm lint && pnpm test && pnpm build`, `python3 scripts/docs_check.py`를 통과시킵니다.

| M0 자산 | M1 작업 |
|---|---|
| [SecurityConfig.kt](../../backend/src/main/kotlin/com/agolock/common/config/SecurityConfig.kt) | `permitAll`·`STATELESS`·CSRF 비활성 설정을 세션·CSRF·인증 규칙으로 교체 |
| [AppProperties.kt](../../backend/src/main/kotlin/com/agolock/common/config/AppProperties.kt) | 메일 `from`·`fromName` 추가, 인증 설정은 `IdentityProperties`로 분리 |
| [PersistenceConfig.kt](../../backend/src/main/kotlin/com/agolock/common/config/PersistenceConfig.kt) | `@MapperScan("com.agolock")`를 `annotationClass = Mapper::class`로 좁힘. `MailPort`·`RateLimiter`가 매퍼로 등록되는 것을 막음 |
| [V1__extensions.sql](../../backend/src/main/resources/db/migration/V1__extensions.sql) | 수정하지 않고 V2부터 추가 |
| [application-contract.yml](../../backend/src/main/resources/application-contract.yml) | DB 제외 contract 프로필을 Testcontainers 기반 `test` 프로필로 대체 |
| [OpenApiExportTest.kt](../../backend/src/test/kotlin/com/agolock/contract/OpenApiExportTest.kt) | `@ActiveProfiles("test")`와 `TestcontainersConfiguration` 사용 |
| [client.ts](../../frontend/src/shared/api/client.ts), [router.tsx](../../frontend/src/app/router.tsx), [app-shell.tsx](../../frontend/src/app/app-shell.tsx) | `/api/v1` base, CSRF·401 처리, 공개·보호 라우트, 실제 세션 데이터 연결 |
| [docs_generate.sh](../../scripts/docs_generate.sh) | `publish` 모드 추가, `docs/generated/` 기록 전환 |

- 의존성은 Spring Session JDBC, Spring Mail, Thymeleaf, BouncyCastle(Argon2id), GreenMail(테스트)을 추가합니다. 버전은 [libs.versions.toml](../../gradle/libs.versions.toml)과 Spring Boot BOM을 따릅니다.
- Spring Session DDL은 의존성 jar의 `org/springframework/session/jdbc/schema-postgresql.sql`을 확인해 그대로 사용합니다.
- [openapi.yaml](../generated/openapi.yaml)과 [contracts.md](../contracts.md)의 M1 10개 경로를 먼저 읽고, 구현 후 생성 결과와 경로·operationId를 대조합니다.

## 3. 데이터 모델과 마이그레이션

스키마 단일 원본은 [데이터 사전](../generated/data-dictionary.md)입니다. 아래는 M1 마이그레이션 체크리스트이며, 소유권·초대 의미 같은 도메인 규칙은 [제품 사양](../product-spec.md)과 [운영](../operations.md)을 따릅니다. 적용 후 데이터 사전을 재생성해 차이를 해소합니다.

| Flyway | 내용 |
|---|---|
| `V2__identity.sql` | `user_status` enum, `app_user`, `space`, `invite_code`, `invite_code_use`, `password_reset_token` |
| `V3__spring_session.sql` | `spring_session`, `spring_session_attributes` |

| 테이블 | 컬럼 | 제약·인덱스 |
|---|---|---|
| `app_user` | `id uuid PK`; `email citext NOT NULL`; `password_hash text NOT NULL`; `display_name text NOT NULL`; `status user_status NOT NULL DEFAULT 'ACTIVE'`; `created_at timestamptz NOT NULL`; `updated_at timestamptz NOT NULL`; `deleted_at timestamptz NULL` | `UNIQUE (email) WHERE deleted_at IS NULL` (`uq_app_user_email_active`); 로그인·재설정 조회는 `deleted_at IS NULL` |
| `space` | `id uuid PK`; `owner_user_id uuid NOT NULL FK app_user(id)`; `name text NOT NULL`; `created_at`, `updated_at timestamptz NOT NULL`; `deleted_at timestamptz NULL` | `UNIQUE (owner_user_id)` (`uq_space_owner_user_id`); 사용자당 하나의 개인 Space |
| `invite_code` | `id uuid PK`; `code_hash text NOT NULL`; `label text NULL`; `max_uses integer NOT NULL CHECK (max_uses > 0)`; `used_count integer NOT NULL DEFAULT 0 CHECK (used_count >= 0)`; `expires_at timestamptz NULL`; `revoked_at timestamptz NULL`; `created_by uuid NULL FK app_user(id)`; `created_at timestamptz NOT NULL` | `UNIQUE (code_hash)` (`uq_invite_code_hash`); `CHECK (used_count <= max_uses)`; 삭제 대신 `revoked_at` |
| `invite_code_use` | `id uuid PK`; `invite_code_id uuid NOT NULL FK invite_code(id)`; `user_id uuid NOT NULL FK app_user(id)`; `used_at timestamptz NOT NULL` | `UNIQUE (invite_code_id, user_id)` (`uq_invite_code_use`) |
| `password_reset_token` | `id uuid PK`; `user_id uuid NOT NULL FK app_user(id)`; `token_hash text NOT NULL`; `expires_at timestamptz NOT NULL`; `used_at timestamptz NULL`; `created_at timestamptz NOT NULL` | `UNIQUE (token_hash)` (`uq_password_reset_token_hash`); `(user_id) WHERE used_at IS NULL` (`idx_password_reset_token_active`) |
| `spring_session` | `primary_id char(36) PK`; `session_id char(36) UNIQUE`; `creation_time bigint`; `last_access_time bigint`; `max_inactive_interval int`; `expiry_time bigint`; `principal_name varchar(100)` | `session_id`, `expiry_time`, `principal_name` 인덱스 |
| `spring_session_attributes` | `session_primary_id char(36) FK spring_session(primary_id) ON DELETE CASCADE`; `attribute_name varchar(200)`; `attribute_bytes bytea` | `PRIMARY KEY (session_primary_id, attribute_name)` |

- `user_status` 값은 `ACTIVE`, `LOCKED`, `DELETED` 세 개만 사용합니다. M1에서 새 상태를 추가하지 않습니다.
- `app_user.deleted_at`이 있으면 `status = 'DELETED'`로 함께 표시합니다. 부분 유니크 인덱스 덕분에 같은 이메일로 재가입할 수 있고, 로그인·재설정·Space 조회는 삭제된 행을 무시합니다.
- `space.deleted_at`은 계정 삭제·운영 도구가 생길 때 사용합니다. M1에는 Space 삭제 API가 없습니다.
- `invite_code`는 soft delete하지 않고 `revoked_at`·`expires_at`으로만 중지합니다. `password_reset_token`은 1회용이며 `used_at` 이후 재사용을 거부합니다.
- 계정 삭제 API가 M1 비목표이므로 soft delete 후 이메일 재사용은 통합 테스트 픽스처에서 `deleted_at`을 직접 설정해 검증합니다.
- `V3`는 `spring-session-jdbc`의 PostgreSQL DDL을 그대로 사용하고 `spring.session.jdbc.initialize-schema=never`로 Flyway만 스키마를 소유합니다. 세션 테이블은 인증 인프라라 `space_id`를 두지 않습니다.
- `spring.session.jdbc.cleanup-cron=0 * * * * *`로 만료 세션을 정리하고, `principal_name`에는 이메일 대신 `user_id`를 저장합니다.

## 4. 백엔드 설계

### 4.1 패키지와 주요 클래스

| 패키지 | 주요 클래스 | 책임 |
|---|---|---|
| `com.agolock.identity.api` | `AuthController`, `dto/AuthDtos.kt` | `/auth/**` 요청·응답, 검증 |
| `com.agolock.identity.domain` | `AppUser`, `UserStatus`, `InviteCode`, `PasswordResetToken` | identity 도메인 값 |
| `com.agolock.identity.persistence` | `AppUserMapper`, `InviteCodeMapper`, `PasswordResetTokenMapper` | MyBatis 매퍼와 XML |
| `com.agolock.identity.service` | `SignupService`, `LoginService`, `PasswordResetService`, `ProfileService`, `SessionService`, `InviteCodeService` | 유스케이스·트랜잭션 |
| `com.agolock.identity.security` | `AuthPrincipal`, `AgolockUserDetailsService`, `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`, `SpaCsrfTokenRequestHandler`, `CsrfCookieFilter`, `AuthenticatedRequestContextFilter` | 인증·CSRF·MDC |
| `com.agolock.identity.cli` | `InviteCodeCli` | 초대코드 생성·철회·목록 |
| `com.agolock.tenancy` | `SpaceController`, `Space`, `SpaceMapper`, `SpaceService`, `Actor`, `ActorContext`, `SpaceOwnership` | Space 생성·이름 수정·소유권 확인, M2용 현재 사용자·소유권 포트 |
| `com.agolock.notification.mail` | `MailPort`, `MailMessage`, `SmtpMailAdapter`, `MailTemplateRenderer` | 메일 발송 포트·SMTP |
| `com.agolock.common.ratelimit`, `common.mybatis.handler` | `RateLimiter`, `InMemoryRateLimiter`, `RateLimitKey`, `RateLimitDecision`, `UserStatusTypeHandler` | 레이트리밋, PostgreSQL enum 바인딩 |

- 매퍼 XML은 `backend/src/main/resources/mapper/identity/*.xml`, `mapper/tenancy/*.xml`에 둡니다.
- 가입은 identity와 tenancy를 함께 쓰는 유일한 유스케이스입니다. 실제 M1 구현은 `AuthService`가 사용자·Space·초대 소비를 같은 트랜잭션에서 처리하고, Space 이름 수정도 `AuthService`가 소유권을 확인합니다. 기능이 커지면 `SignupService`·`SpaceService`로 분리합니다.
- 실제 인증 principal 이름은 `AppUserPrincipal`입니다.
- `AuthPrincipal`은 `UserDetails`와 `Serializable`을 구현하고 `userId`, `spaceId`, `email`, `displayName`, `status`만 담습니다. `username`은 `userId.toString()`으로 두어 세션 인덱스 키를 안정적으로 유지합니다.
- `Actor(userId, ownSpaceId)`는 세션에서 복원해 `@AuthenticationPrincipal` 또는 argument resolver로 제공하고, `SpaceOwnership` 포트가 `space.owner_user_id`를 조회합니다. M2의 `AccessService`가 이 포트를 재사용합니다.

### 4.2 Security 필터 체인

- `csrf`: `CookieCsrfTokenRepository.withHttpOnlyFalse()`와 `SpaCsrfTokenRequestHandler`. 쿠키 `XSRF-TOKEN`, 헤더 `X-XSRF-TOKEN`, 예외 경로 없음.
- `sessionManagement`: `IF_REQUIRED`, `changeSessionId()`. `maximumSessions(5)`는 `SpringSessionBackedSessionRegistry`로 계산하고 초과 시 가장 오래된 세션을 만료시킵니다(`maxSessionsPreventsLogin=false`).
- `securityContext`: `requireExplicitSave(true)`. 로그인·가입 컨트롤러가 `SecurityContextRepository.saveContext`를 직접 호출합니다.
- `authorizeHttpRequests`: `/api/v1/auth/config`, `/auth/csrf`, `/auth/signup`, `/auth/login`, `/auth/password-reset/**`, `/actuator/health/**`, `/actuator/info`는 공개. 나머지 `/api/v1/**`는 인증 필요. 정적 SPA는 허용합니다.
- `exceptionHandling`: 401은 `JsonAuthenticationEntryPoint`, 403은 `JsonAccessDeniedHandler`가 problem+json으로 반환하고 CSRF 실패는 `CSRF_INVALID`로 구분합니다.
- `formLogin`, `httpBasic`, 기본 `logout` 필터는 끕니다. CORS는 same-origin 기본이고 `agolock.publicBaseUrl`만 `allowCredentials=true`로 허용합니다.
- 보안 헤더는 기본값에 `Referrer-Policy: no-referrer`를 추가해 재설정 토큰이 Referer로 새지 않게 합니다.

### 4.3 세션과 쿠키

| 항목 | 값 |
|---|---|
| 저장소 | Spring Session JDBC, `JdbcIndexedSessionRepository` |
| 쿠키 | `SESSION`, `HttpOnly=true`, `SameSite=Lax`, `Path=/`, `Secure`는 local false·staging/production true |
| 타임아웃 | 30분 (`spring.session.timeout=${AUTH_SESSION_TIMEOUT:30m}`) |
| URL 추적 | 비활성 (`server.servlet.session.tracking-modes=cookie`) |
| 로그인 | `changeSessionId()` 후 SecurityContext 저장 |
| 로그아웃·재설정 완료 | 해당 사용자의 모든 세션 무효화, 현재 쿠키 삭제 |

- [application.yml](../../backend/src/main/resources/application.yml)의 `server.servlet.session.cookie.*`와 `spring.session.*`을 사용하고, `agolock.environment`가 `local`이 아니면 `Secure=false`일 때 기동을 실패시킵니다.
- 세션에서 읽은 표시 이름은 낡을 수 있으므로 `GET /auth/me`는 `user_id`로 DB를 다시 조회합니다.

### 4.4 비밀번호와 초대코드 해싱

- 비밀번호는 Argon2id입니다. `DelegatingPasswordEncoder`에 `argon2`를 기본 인코더로 등록하고 `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`을 사용합니다. 저장 값은 `{argon2}$argon2id$...` 형식입니다.
- BouncyCastle `bcprov-jdk18on`을 추가하고, 로그인 성공 시 `upgradeEncoding`이 참이면 새 파라미터로 재해시합니다.
- 비밀번호 정책은 길이 10~128자만 강제합니다. 앞뒤 공백을 자르지 않고 조합 규칙을 임의로 추가하지 않습니다.
- 초대코드는 `AGO-` + Crockford Base32 20자(100비트)입니다. 하이픈·공백을 제거하고 대문자로 정규화한 뒤 SHA-256 hex를 `code_hash`에 저장합니다. 코드 원문은 CLI 표준출력에 한 번만 표시하고 로그에 남기지 않습니다.
- 초대코드 만료 기본값은 7일, `max_uses`는 CLI 필수 인자입니다. 충분히 긴 무작위 값이므로 M1은 pepper 없는 SHA-256을 사용합니다.

### 4.5 레이트리밋과 로그인 잠금

`InMemoryRateLimiter`는 `Clock`을 주입받는 고정 윈도 카운터입니다. 이메일은 원문 대신 SHA-256 짧은 해시로 키잉하고, 429에는 `Retry-After`를 넣습니다.

| 대상 | 키 | 한도 |
|---|---|---|
| 로그인 | IP + 이메일 해시 | 10회/분 ([운영](../operations.md) 5.5) |
| 로그인 | IP | 30회/분 (M1 권장, 확인 필요) |
| 가입·초대코드 검증 | IP | 10회/시간 (M1 권장, 확인 필요) |
| 재설정 요청 | 이메일 해시 / IP | 5회/시간 / 20회/시간 (M1 권장, 확인 필요) |
| 재설정 확인 | IP | 10회/분 (M1 권장, 확인 필요) |

- 로그인 실패 10회/15분 잠금은 `(IP, 이메일)` 키의 실패 카운터로 구현해 429를 반환합니다. `app_user.status=LOCKED`는 운영자 조치용으로만 사용하고 M1에서 계정 단위 잠금 컬럼을 추가하지 않습니다.
- 단일 인스턴스 전제이며, 다중 인스턴스 전환 시 `RateLimiter` 구현만 Redis로 교체합니다.
- IP는 `request.remoteAddr`를 사용하고, 리버스 프록시 뒤에서는 staging/production에서만 `server.forward-headers-strategy=framework`를 켭니다.

### 4.6 메일 포트와 템플릿

- `MailPort.send(MailMessage)`를 `SmtpMailAdapter`가 `JavaMailSenderImpl`로 구현합니다. `MailMessage`는 `to`, `subject`, `htmlBody`, `textBody`를 가집니다.
- `backend/src/main/resources/mail/password-reset.html`, `password-reset.txt`를 Thymeleaf로 렌더링합니다. 제목은 `[AGO Lock] 비밀번호 재설정 안내`, 본문에는 표시 이름, 재설정 링크, 만료 30분, 미요청 시 무시 안내를 넣습니다.
- SMTP 연결·읽기 타임아웃은 3초입니다. 로컬은 Mailpit(`localhost:1025`, UI `http://localhost:8025`)을 사용합니다.
- 토큰 저장 트랜잭션이 커밋된 뒤 `@TransactionalEventListener(AFTER_COMMIT)`에서 발송합니다. 실패는 `mail.send.failure` 카운터와 `request_id` 로그만 남기고 202를 유지합니다.
- M1에는 가입 환영 메일을 보내지 않으므로 메일 장애가 가입·로그인을 막지 않습니다. 영속 재시도 큐·DLQ는 M5 Outbox에서 추가하고 그때 [운영](../operations.md) 5.5 문구를 맞춥니다.

### 4.7 트랜잭션 경계

| 유스케이스 | 트랜잭션 | 경계 밖 |
|---|---|---|
| 가입 | `invite_code` `SELECT FOR UPDATE` → 재검증 → `used_count` 증가 → `app_user`·`space`·`invite_code_use` insert | Argon2id 해시, 세션 생성 |
| 로그인 | `@Transactional(readOnly=true)` 사용자·Space 조회 | 세션 회전·저장, 실패 카운터 |
| 표시 이름·Space 이름 수정 | 단일 update | 없음 |
| 재설정 요청 | 이전 미사용 토큰 무효화 + 새 토큰 insert | 메일 렌더링·발송 |
| 재설정 확인 | 토큰 `SELECT FOR UPDATE` → 검증 → `password_hash` update → `used_at` → 다른 토큰 무효화 | 전체 세션 무효화 |
| 로그아웃 | 없음 | 세션 저장소에서 전체 세션 삭제 |

- Argon2id는 수십~수백 ms가 걸리므로 DB 트랜잭션 밖에서 계산합니다. 초대코드는 1차 조회로 빠르게 검증한 뒤 트랜잭션 안에서 락과 재검증을 수행합니다.
- 이메일 중복으로 insert가 실패하면 `DuplicateKeyException`을 `EMAIL_ALREADY_EXISTS`로 변환하고 `used_count` 증가도 롤백합니다.

### 4.8 오류 코드와 상태

| 코드 | 상태 | 상황 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 형식·필수 필드·길이 오류 |
| `INVITE_CODE_REQUIRED` | 400 | 초대코드 필수 환경에서 누락 |
| `EMAIL_ALREADY_EXISTS` | 409 | 활성 이메일 중복 |
| `INVALID_CREDENTIALS` | 401 | 이메일·비밀번호 불일치(계정 존재 여부 비노출) |
| `AUTHENTICATION_REQUIRED` | 401 | 세션 없음·만료 |
| `CSRF_INVALID` | 403 | CSRF 토큰 누락·불일치 |
| `PERMISSION_DENIED` | 403 | Space 소유자가 아님 |
| `RESOURCE_NOT_FOUND` | 404 | 없는 Space |
| `ACCOUNT_LOCKED` | 403 | 자격 증명은 맞지만 `status=LOCKED` |
| `INVITE_CODE_INVALID` | 422 | 만료·철회·소진·존재하지 않음 |
| `PASSWORD_RESET_TOKEN_INVALID` | 422 | 만료·사용됨·존재하지 않음 |
| `RATE_LIMITED` | 429 | 레이트리밋·실패 잠금, `Retry-After` 포함 |
| `INTERNAL_ERROR` | 500 | 처리하지 못한 예외 |

- 기존 [GlobalExceptionHandler.kt](../../backend/src/main/kotlin/com/agolock/common/error/GlobalExceptionHandler.kt)를 그대로 사용하고 `ApiException`으로 위 코드를 던집니다.
- 인증 실패는 `INVALID_CREDENTIALS` 하나로 통일하고, `ACCOUNT_LOCKED`는 비밀번호가 맞은 뒤에만 반환합니다.

## 5. API 계약

최종 OpenAPI는 M1 전까지 [docs/generated/openapi.yaml](../generated/openapi.yaml) 설계 초안이고, M1에서 컨트롤러와 테이블이 생기면 [ADR-015](../decisions/ADR-015-contract-preview-mode.md)에 따라 기록 모드로 전환합니다. 엔드포인트 규칙의 단일 원본은 [contracts.md](../contracts.md) 5.1입니다.

| Method·Path | 인증·CSRF | 요청 | 성공 응답 | 주요 오류 |
|---|---|---|---|---|
| `GET /api/v1/auth/config` | 공개, CSRF 없음 | 없음 | 200 `AuthConfig` (`inviteRequired`, `passwordMinLength`, `passwordMaxLength`) | 없음 |
| `GET /api/v1/auth/csrf` | 공개, CSRF 없음 | 없음 | 200 `CsrfTokenResponse` (`token`), `XSRF-TOKEN` 쿠키 | 없음 |
| `POST /api/v1/auth/signup` | 공개, CSRF 필요 | `email`, `password`, `displayName`, `inviteCode?` | 201 `AuthMe` (`user`, `space`), `Location: /api/v1/auth/me`, `SESSION` 쿠키 | 400, 409, 422, 429 |
| `POST /api/v1/auth/login` | 공개, CSRF 필요 | `email`, `password` | 200 `AuthMe`, 새 `SESSION` 쿠키 | 401, 403, 429 |
| `POST /api/v1/auth/logout` | 인증, CSRF 필요 | 없음 | 204 | 401, 403 |
| `GET /api/v1/auth/me` | 인증 | 없음 | 200 `AuthMe` | 401 |
| `PATCH /api/v1/auth/me` | 인증, CSRF 필요 | `displayName` | 200 `User` | 400, 401, 403 |
| `POST /api/v1/auth/password-reset/request` | 공개, CSRF 필요 | `email` | 202, 본문 없음 | 429 |
| `POST /api/v1/auth/password-reset/confirm` | 공개, CSRF 필요 | `token`, `newPassword` | 204 | 400, 422, 429 |
| `PATCH /api/v1/spaces/{spaceId}` | OWNER, CSRF 필요 | `name` | 200 `Space` | 400, 401, 403, 404 |

- `AuthConfig`는 가입 화면의 초대코드 필드와 비밀번호 길이를 결정합니다. `AuthMe`는 `user`(`id`, `email`, `displayName`, `status`)와 `space`(`id`, `name`, `ownerUserId`)를 포함합니다.
- `PATCH`는 JSON Merge Patch 의미를 따르되 M1의 두 요청은 필수 필드 하나만 받습니다. `null`·빈 문자열은 400입니다.
- 모든 인증 응답은 `Cache-Control: no-store`입니다. 429는 `RATE_LIMITED`와 `Retry-After`를 사용합니다.
- `GET /auth/csrf`는 XOR 처리된 요청 속성 토큰이 아니라 `CookieCsrfTokenRepository.loadToken(request)`의 원문 토큰을 반환해 쿠키 값과 헤더 값이 일치하게 합니다.
- `Idempotency-Key`는 M1에서 구현하지 않습니다. 가입 재시도는 409로 수렴하고, M2가 생성 API에 필요하면 `V5__idempotency.sql`로 추가합니다.
- 초대코드 무효는 422 `INVITE_CODE_INVALID`로 두고 OpenAPI signup 응답에 422를 추가합니다. 현재 초안의 400·409·429만으로는 계약 3.1의 “비즈니스 검증 422”와 맞지 않습니다.
- operationId는 초안과 동일하게 `getAuthConfig`, `getCsrfToken`, `signup`, `login`, `logout`, `getMe`, `updateMe`, `requestPasswordReset`, `confirmPasswordReset`, `updateSpace`를 유지합니다.
- springdoc의 `CsrfOpenApiCustomizer`가 변경 메서드에 `X-XSRF-TOKEN` 파라미터를 추가하고, `Problem`·`AuthConfig` 기본값·`User.status` enum이 초안과 일치하도록 `@Schema` 또는 customizer를 사용합니다.

## 6. 프론트엔드 설계

### 6.1 라우트와 화면

| 라우트 | 화면 | 접근 | 동작 |
|---|---|---|---|
| `/login` | SCR-001 로그인 | 공개 | 로그인, 재설정·가입 링크, 429 안내 |
| `/signup` | SCR-002 회원가입 | 공개 | 표시 이름·이메일·비밀번호·초대코드, 성공 시 자동 로그인 |
| `/reset-password` | SCR-001 재설정 요청 | 공개 | 이메일 입력, 항상 같은 202 안내 |
| `/reset-password/confirm?token=...` | SCR-001 새 비밀번호 | 공개 | 토큰 검증, 새 비밀번호, 로그인 이동 |
| `/settings` | SCR-017 설정 | 보호 | 프로필·Space·테마·로그아웃·앱 정보 |
| `/` | M1 홈 | 보호 | 사용자·Space 인사, 설정 링크 |
| `/space`, `/inventory`, `/sharing` | 자리표시자 | 보호 | M2·M3·M4 안내 유지 |
| `/design-preview` | 디자인 미리보기 | 개발 공개 | 제품 화면 아님, 홈 링크는 개발 빌드에서만 노출 |
| `*` | 404 | 공개 | 기존 오류 화면 |

- `ProtectedRoute`는 `/auth/me` 401이면 `/login?redirect=<현재 경로>`로 이동합니다. `redirect`는 `/`로 시작하고 `//`가 아닌 값만 허용해 오픈 리다이렉트를 막습니다.
- `PublicOnlyRoute`는 로그인 사용자가 `/login`·`/signup`에 오면 `/`로 보냅니다. 재설정 화면은 로그인 상태에서도 허용합니다.
- 인증 부트스트랩 중에는 전체 화면 스켈레톤을 보여 주고 401을 토스트하지 않습니다.

### 6.2 API 클라이언트와 상태

- [client.ts](../../frontend/src/shared/api/client.ts)에 `API_BASE = '/api/v1'`를 추가하고 Vite의 `/api` 프록시를 유지합니다.
- 변경 요청 전에 `XSRF-TOKEN`이 없으면 `GET /auth/csrf`를 먼저 호출합니다. 403 `CSRF_INVALID`는 토큰을 한 번만 재발급하고 같은 요청을 1회 재시도합니다.
- 401은 `onUnauthorized` 콜백으로 `AuthProvider`에 전달합니다. `/auth/me` 부트스트랩의 401은 세션 없음으로, 그 외 401은 `queryClient.clear()` 후 로그인으로 처리합니다.
- TanStack Query 키는 `['auth','me']`, `['auth','config']`입니다. 로그인·가입 성공 시 `AuthMe`를 캐시에 넣고 프로필·Space 수정 시 해당 캐시를 갱신합니다.
- `frontend/src/shared/api/auth.ts`에 `getAuthConfig`, `getCsrfToken`, `signup`, `login`, `logout`, `getMe`, `updateMe`, `updateSpace`, `requestPasswordReset`, `confirmPasswordReset` 타입 함수를 둡니다.

### 6.3 컴포넌트와 폼

| 용도 | 재사용 | 신규 |
|---|---|---|
| 버튼·카드·뱃지 | `Button`, `Card`, `CardBody`, `Badge` | 없음 |
| 제목·보조문 | `PageTitle`, `MetaText` | 없음 |
| 테마 | `ThemeToggle`, `ThemeProvider` | 설정의 `RadioGroup` |
| 사용자 | `Avatar` | 없음 |
| 입력·피드백 | 없음 | `TextField`, `PasswordField`, `FormField`, `Alert`, `Toast`, `AuthLayout` |

- 새 컴포넌트는 [globals.css](../../frontend/src/styles/globals.css)의 토큰만 사용합니다. 테두리 2.5px, 하드 섀도, radius 0, Noto Sans KR, 44px 터치 영역을 지킵니다.
- 입력 높이는 48px, 라벨은 12px/900, 오류 문구는 `role="alert"`로 연결합니다. 비밀번호 표시 토글은 `aria-pressed`와 접근 가능한 이름을 가집니다.
- 오류 색상 토큰은 현재 디자인 시스템에 없습니다. M1에서는 `--primary`를 오류에 재사용하지 않고 `--danger` 역할 토큰 추가를 권장합니다(11번 항목 확인 필요). 추가 전에는 굵은 텍스트·아이콘·`role="alert"`로 색에 의존하지 않게 합니다.
- 폼은 React Hook Form + Zod를 사용합니다. 로그인은 이메일·비밀번호, 가입은 표시 이름·이메일·비밀번호·확인·조건부 초대코드, 재설정은 이메일 또는 토큰·새 비밀번호·확인을 검증합니다.
- 제출 중에는 버튼을 비활성화하고 `aria-busy`를 표시합니다. 성공 시 토스트와 이동, 실패 시 폼 상단 `Alert`와 필드 오류를 함께 보여 줍니다.
- 서버 `fieldErrors`는 `email`, `password`, `displayName`, `inviteCode`, `name`, `newPassword`, `token`에 매핑합니다. `EMAIL_ALREADY_EXISTS`는 이메일, `INVITE_CODE_INVALID`는 초대코드, `PASSWORD_RESET_TOKEN_INVALID`는 토큰 오류 화면으로 보냅니다.
- 429는 `Retry-After`를 읽어 “N초 후 다시 시도”를 표시하고 제출을 잠급니다. 로그인 401은 “이메일 또는 비밀번호가 올바르지 않습니다.”로 통일합니다.
- 세션 만료는 “세션이 만료됐어요. 다시 로그인해 주세요.” 토스트 후 로그인으로 이동합니다. 저장 중이던 폼 값은 유지하지 않습니다.

### 6.4 화면별 요구와 접근성

- 로그인: 이메일, 비밀번호, 로그인, “비밀번호를 잊었어요”, “회원가입”. 성공 후 `redirect` 또는 `/`로 이동합니다.
- 회원가입: 표시 이름, 이메일, 비밀번호, 확인, `inviteRequired=true`일 때만 초대코드. 정책 문구는 `/auth/config` 값으로 만들고 성공 시 홈으로 이동합니다.
- 재설정 요청: 이메일을 받고 존재 여부와 무관하게 “메일을 보냈어요”를 표시합니다. 확인: 토큰이 없거나 만료되면 재요청 링크를 보여 줍니다.
- 설정: 프로필(표시 이름), Space(이름·소유자 뱃지), 화면(시스템·라이트·다크), 계정(로그아웃), 앱 정보(버전·환경). 카테고리 관리는 M3 안내로 남깁니다.
- 모바일 390px 단일 컬럼·좌우 16px·하단 탭, 데스크톱 설정 본문 최대 720px·인증 카드 420px입니다. 모든 입력에 라벨, `autocomplete`, 오류 `aria-describedby`, 첫 오류 포커스, 라우트 전환 `h1` 포커스, 테마 `fieldset`·`legend`를 적용합니다.

### 6.5 목업에서 실제로 바뀌는 것

| 현재 | M1 이후 |
|---|---|
| `/settings` 자리표시자 | SCR-017 실제 화면 |
| AppShell의 “지민”, “민지네 베란다”, “M0 FOUNDATION”, “API 연결 전” | 실제 표시 이름·Space 이름·환경 뱃지. 공유 Space 항목은 M4까지 숨김 |
| M0 홈 체크리스트 | M1 홈 인사·Space·설정 링크 |
| 홈의 디자인 미리보기 링크 | 개발 빌드에서만 노출 |
| 로그인·가입 화면 없음 | SCR-001·SCR-002 신규 |
| `/design-preview` | 제품 화면 아님, 그대로 유지 |

## 7. 작업 분해

1. **설정**: `gradle/libs.versions.toml`에 spring-session-jdbc, mail, thymeleaf, bouncycastle, greenmail 추가. `IdentityProperties.kt`와 `application*.yml`에 auth·session·mail 설정, `ConfigValidationTest`에 non-local Secure 검증. 검증: `./gradlew :backend:test --tests '*ConfigValidationTest'`.
2. **마이그레이션**: `V2__identity.sql`~`V3__spring_session.sql`, `PersistenceConfig` 매퍼 스캔 축소, `DatabaseMigrationTest`에 테이블·enum·제약·세션 검증. 검증: `./gradlew :backend:test --tests '*DatabaseMigrationTest'`.
3. **영속성**: `AppUserMapper`, `SpaceMapper`, `InviteCodeMapper`, `PasswordResetTokenMapper`와 XML, `UserStatusTypeHandler`. 검증: `./gradlew :backend:test --tests '*MapperTest'`.
4. **보안 기반**: `SecurityConfig.kt`, `SessionConfig.kt`, `PasswordConfig.kt`, `AuthPrincipal`, `AgolockUserDetailsService`, 401·403 핸들러, CSRF 필터·핸들러. 검증: `./gradlew :backend:test --tests '*CsrfIntegrationTest' --tests '*SecurityConfigTest'`.
5. **가입·초대코드**: `SignupService`, `InviteCodeService`, `InviteCodeCli`, `POST /auth/signup`. 검증: `./gradlew :backend:test --tests '*AuthSignupIntegrationTest'`.
6. **로그인·세션**: `LoginService`, `SessionService`, `/auth/login`, `/auth/logout`, `/auth/me`, `/auth/config`, `/auth/csrf`. 세션 회전·최대 5개·전체 무효화. 검증: `./gradlew :backend:test --tests '*AuthLoginSessionIntegrationTest'`.
7. **비밀번호 재설정**: `PasswordResetService`, `MailPort`, `SmtpMailAdapter`, Thymeleaf 템플릿, API 2종. 검증: `./gradlew :backend:test --tests '*PasswordResetIntegrationTest' --tests '*MailDeliveryIntegrationTest'`.
8. **Space·프로필**: `SpaceService`, `SpaceController`, `ProfileService`, `PATCH /auth/me`, `PATCH /spaces/{spaceId}`. 검증: `./gradlew :backend:test --tests '*SpaceSettingsIntegrationTest'`.
9. **레이트리밋·관측**: `InMemoryRateLimiter`를 로그인·가입·재설정에 연결, `AuthenticatedRequestContextFilter`로 `user_id`·`space_id` MDC, Micrometer 카운터. 검증: `./gradlew :backend:test --tests '*RateLimitIntegrationTest' --tests '*AuthLoggingIntegrationTest'`.
10. **계약 기록 모드**: springdoc annotation·customizer로 operationId·CSRF 파라미터 일치. `OpenApiExportTest`를 Testcontainers `test` 프로필로 변경, `docs_generate.sh publish`·`normalize_openapi.py --mode publish`·`generate_data_dictionary.py --mode publish` 추가. 검증: `bash scripts/docs_generate.sh publish && python3 scripts/docs_check.py && pnpm generate:api`.
11. **프론트엔드 기반**: `client.ts` CSRF·401, `auth-provider.tsx`, `protected-route.tsx`, `auth-layout.tsx`, `text-field.tsx`, `form-field.tsx`, `alert.tsx`, `toast.tsx`, `radio-group.tsx`, `auth.ts`. 검증: `pnpm test -- --run src/features/auth && pnpm lint`.
12. **화면·마무리**: `login-page.tsx`, `signup-page.tsx`, `reset-password-page.tsx`, `reset-password-confirm-page.tsx`, `settings-page.tsx`, 홈·AppShell 실제 데이터, 라우터 교체. Playwright 인증 흐름, CI diff, `docs/contracts.md` 7번 항목·`docs/index.yaml` 갱신. 검증: `pnpm test && pnpm build && pnpm test:e2e && python3 scripts/docs_check.py`.

## 8. 테스트

### 8.1 백엔드 단위

| 테스트 | 시나리오 |
|---|---|
| `PasswordEncoderTest` | Argon2id 해시 생성·검증, 잘못된 비밀번호 거부, 호출마다 다른 salt |
| `EmailNormalizerTest` | 대소문자·앞뒤 공백 정규화, 빈 값 거부 |
| `InviteCodeHasherTest` | 정규화, 결정적 해시, 다른 코드와 충돌 없음 |
| `PasswordPolicyTest` | 9자 거부, 10자·128자 허용, 129자 거부 |
| `RateLimiterTest` | 한도 이내 허용, 초과 429, 고정 Clock 윈도 리셋, 키 격리 |
| `IdentityPropertiesValidationTest` | non-local `Secure=false` 기동 실패, 잘못된 TTL 거부 |
| `MailTemplateRendererTest` | HTML·텍스트에 표시 이름·URL·만료 포함, 토큰 미로깅 |
| `AuthErrorMappingTest` | 401·403·422·429 problem+json 코드와 `Retry-After` |

### 8.2 백엔드 통합(Testcontainers)

| 테스트 | 대표 시나리오 |
|---|---|
| `AuthSignupIntegrationTest` | 초대코드 불필요 환경 가입 성공·Space·세션 생성; 누락 400; 만료·철회·소진 422; 중복 이메일 409와 `used_count` 롤백; soft delete 이메일 재가입; 마지막 코드 동시 요청 1회 성공 |
| `AuthLoginSessionIntegrationTest` | 로그인 시 세션 ID 회전; `SESSION` HttpOnly·SameSite=Lax; 5개 초과 시 최고 오래된 세션 만료; 로그아웃 시 전체 세션 무효화; 만료 후 `/auth/me` 401; 계정 존재 여부 비노출 |
| `CsrfIntegrationTest` | `/auth/csrf`가 쿠키·토큰 반환; CSRF 없는 로그인·가입 403; 올바른 헤더 통과; 재발급 후 이전 토큰 거부 |
| `PasswordResetIntegrationTest` | 없는 이메일도 202; 메일 발송; 만료·재사용 토큰 422; 완료 후 기존 비밀번호 실패·새 비밀번호 성공; 전체 세션 무효화; 재요청 시 이전 토큰 무효화 |
| `SpaceSettingsIntegrationTest` | 소유자 이름 변경; 다른 사용자 Space 403; 없는 Space 404; 표시 이름 80자·Space 이름 120자 초과 400 |
| `RateLimitIntegrationTest` | 로그인 11회째 429와 `Retry-After`; 가입·재설정 한도 429; 윈도 경과 후 허용 |
| `MailDeliveryIntegrationTest` | GreenMail로 제목·수신자·HTML·텍스트 검증; SMTP 실패 시 202 유지·오류 카운터 |
| `MigrationSchemaTest` | V2~V3 적용, enum 값, 부분 유니크, `used_count <= max_uses`, 세션 테이블 |
| `TenantIsolationFoundationTest` | A가 B의 Space를 수정 불가; B의 `/auth/me`와 A의 세션 분리 |
| `AuthLoggingIntegrationTest` | `request_id`·`user_id`·`space_id` 포함, 비밀번호·토큰·세션 ID·초대코드 원문 없음 |
| `OpenApiContractTest` | M1 10개 operationId, `X-XSRF-TOKEN`, `Problem`·`AuthConfig`, 중복 operationId 없음 |

### 8.3 프론트엔드

| 테스트 | 시나리오 |
|---|---|
| `login-page.test.tsx` | 필수 입력, 성공 이동, 401 인라인 오류, 429 카운트다운, 재설정·가입 링크 |
| `signup-page.test.tsx` | `inviteRequired`일 때만 초대코드, 비밀번호 확인 불일치, 409 이메일, 422 초대코드, 성공 자동 로그인 |
| `reset-password-page.test.tsx` | 202 일반 안내, 토큰 없음·만료, 새 비밀번호 확인, 성공 후 로그인 이동 |
| `settings-page.test.tsx` | 표시 이름·Space 이름 저장, 서버 필드 오류, 테마 3모드, 로그아웃, 세션 만료 이동 |
| `protected-route.test.tsx` | 미인증 `/login?redirect=...`, 인증 렌더, 부트스트랩 스켈레톤, 오픈 리다이렉트 차단 |
| `auth-client.test.ts` | CSRF 선호출, 403 1회 재시도, 401 콜백, problem+json 파싱 |
| `app-shell.test.tsx` | 실제 표시 이름·Space 이름, 가짜 공유 공간 없음, 설정 이동 |

### 8.4 Playwright

| 파일 | 시나리오 |
|---|---|
| `frontend/e2e/auth.spec.ts` | 가입→홈, 로그아웃→로그인, 로그인→설정, 잘못된 비밀번호, 세션 쿠키 삭제 후 보호 라우트→로그인 |
| `frontend/e2e/auth-mobile.spec.ts` | 390px 가입·로그인 스크롤, 44px 터치, 하단 탭 유지 |
| `frontend/e2e/settings.spec.ts` | 표시 이름·Space 이름 수정 후 새로고침 유지, 테마 전환, 로그아웃 |

- M1 Playwright는 `page.route('**/api/v1/**')`로 API를 모킹해 UI 흐름을 검증합니다. 실제 세션·CSRF·메일은 백엔드 Testcontainers 테스트가 담당하고, 전체 스택 E2E는 M8에서 추가합니다.

## 9. 관측·보안·운영

### 9.1 로그와 메트릭

- 기존 `RequestIdFilter`의 `X-Request-Id`·MDC `request_id`를 유지하고, `AuthenticatedRequestContextFilter`가 `user_id`, `space_id`를 추가합니다.
- 로그에 이메일 원문, 비밀번호, 세션 ID, 초대코드 원문, 재설정 토큰, CSRF 토큰을 남기지 않습니다. 이메일이 필요하면 SHA-256 짧은 해시를 사용합니다.
- Micrometer 카운터: `auth.signup.success`, `auth.signup.failure`, `auth.login.success`, `auth.login.failure`, `auth.password_reset.requested`, `auth.password_reset.completed`, `auth.session.expired`, `rate_limit.rejected`, `mail.send.failure`.
- local에서만 `management.endpoints.web.exposure.include`에 `metrics`를 추가하고 staging/production은 health·info만 노출합니다.

### 9.2 설정과 비밀

| 변수 | 용도 | 비고 |
|---|---|---|
| `AUTH_SIGNUP_INVITE_REQUIRED` | 가입 초대코드 필수 | local true, staging true, production false |
| `AUTH_SESSION_TIMEOUT`, `AUTH_SESSION_MAX` | 세션 만료·동시 세션 | 30m, 5 |
| `AUTH_PASSWORD_RESET_TTL` | 재설정 토큰 TTL | 30m |
| `SESSION_COOKIE_SECURE` | 세션 쿠키 Secure | local false, 그 외 true 강제 |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `MAIL_FROM_NAME` | SMTP | local Mailpit |
| `PUBLIC_BASE_URL` | 재설정 링크 | local `http://localhost:5173` |

- 비밀 값은 `.env.local`과 운영 시크릿 관리자에만 둡니다. `SESSION_COOKIE_SECURE`, `PUBLIC_BASE_URL`은 non-local에서 검증합니다.
- 초대코드 CLI 예시입니다.

```bash
./gradlew :backend:bootRun --args='--spring.profiles.active=local invite-code create --label staging --max-uses 20 --expires-in 7d'
./gradlew :backend:bootRun --args='--spring.profiles.active=local invite-code revoke --id <uuid>'
./gradlew :backend:bootRun --args='--spring.profiles.active=local invite-code list'
```

### 9.3 마이그레이션과 계약

- 배포 순서는 V2~V3 적용, 새 애플리케이션 배포, 헬스 체크, 가입·로그인 스모크입니다. 마이그레이션은 additive이므로 이전 애플리케이션도 기동할 수 있습니다.
- M1에서 `docs/generated/openapi.yaml`과 `docs/generated/data-dictionary.md`를 생성 기록 모드로 전환합니다. 생성 헤더를 “생성 문서”로 바꾸고 [docs_check.py](../../scripts/docs_check.py)가 이를 허용하도록 수정합니다.
- `docs_generate.sh publish`는 `build/contracts/`가 아니라 `docs/generated/`에 기록합니다. `pnpm generate:api`는 갱신된 OpenAPI에서 타입을 다시 만듭니다.
- [ADR-015](../decisions/ADR-015-contract-preview-mode.md)의 M1 전환 조건을 이행하고 전환 날짜·명령을 ADR에 기록합니다. [ADR-014](../decisions/ADR-014-generated-docs-draft.md)의 “직접 수정 금지”가 다시 적용됩니다.
- [docs/index.yaml](../index.yaml)의 PLAN-002 등록과 `last_verified_commit` 갱신은 이 문서 작성 범위에서 하지 않습니다.

### 9.4 CI

| Job | M1 변경 |
|---|---|
| docs | `docs_check.py`가 생성 헤더와 PLAN-002 링크를 검사 |
| backend | `ktlintCheck detekt test` 유지, Testcontainers 테스트에 GreenMail 추가 |
| frontend | `pnpm generate:api` diff, lint·format·test·build·Playwright 유지 |
| contract | Testcontainers `test` 프로필로 OpenAPI 미리보기 생성, `docs_generate.sh all` 후 `docs_check.py` 확인 |

- 데이터 사전 diff는 PostgreSQL 서비스와 `bootRun --args='--spring.main.web-application-type=none --agolock.migrate-only=true'` 같은 마이그레이션 전용 실행으로 추가합니다. 구현이 늦어지면 M8 생성기 완성 시 추가합니다.
- Mailpit은 로컬 수동 검증, CI는 GreenMail로 SMTP를 검증합니다.

## 10. 완료 체크리스트와 종료 조건

- [x] 초대코드 필수·선택 환경이 설정으로 분리되고 두 경우 모두 테스트됨
- [x] Argon2id 해시와 `upgradeEncoding`이 테스트됨
- [x] 로그인 세션 회전, 최대 5세션, 로그아웃 전체 무효화가 테스트됨
- [x] CSRF 발급·검증·재발급과 403 `CSRF_INVALID`가 테스트됨
- [x] 재설정 메일이 Mailpit 또는 GreenMail로 발송됨
- [x] 재설정 완료 후 전체 세션 무효화와 토큰 1회용이 테스트됨
- [x] 이메일 중복 409와 soft delete 후 재사용이 테스트됨
- [x] 메일 발송 실패가 가입·로그인을 막지 않음
- [x] 로그인·가입·재설정 레이트리밋과 `Retry-After`가 테스트됨
- [x] 다른 Space 이름을 바꿀 수 없고 교차 Space 테스트 기반이 있음
- [x] SCR-001·SCR-002·SCR-017이 실제 API와 연결됨
- [x] 키보드·라벨·오류 `role="alert"`·44px 터치가 확인됨
- [x] OpenAPI·데이터 사전 재생성, `pnpm generate:api` diff 없음
- [x] `docs_check.py`와 CI 4개 job 통과

```bash
./gradlew :backend:ktlintCheck :backend:detekt :backend:test
pnpm lint && pnpm format:check && pnpm test && pnpm build
pnpm test:e2e
bash scripts/docs_generate.sh all
pnpm generate:api
python3 scripts/docs_check.py
```

## 11. 위험과 확인 필요

| 항목 | 상태 | 권장안 |
|---|---|---|
| 로그인 실패 잠금 저장 위치 | 확인 필요 | M1은 `(IP, 이메일)` in-memory 카운터로 429. 계정 단위 잠금이 필요하면 `app_user.failed_login_count`·`locked_until` 추가와 데이터 사전 갱신 |
| 로그아웃 범위 | 확인 필요 | 계약 2번 항목대로 전체 세션 무효화. 현재 기기만 끊는 UX를 원하면 계약을 먼저 수정 |
| 초대코드 무효 상태 | 확인 필요 | 422 `INVITE_CODE_INVALID`로 통일하고 OpenAPI signup 응답에 422 추가. 400 유지 시 계약 3.1과 예외 처리 |
| 가입·재설정 레이트리밋 값 | 확인 필요 | 표의 M1 권장값으로 시작하고 운영 5.5에 반영 |
| 메일 재시도 | 확인 필요 | M1은 동기 발송 + 실패 로그·카운터, 202 유지. 영속 재시도는 M5 Outbox에서 구현하고 운영 문구를 그때 맞춤 |
| 오류 색상 토큰 | 확인 필요 | `--danger` 역할 토큰 추가와 WCAG AA 검증. 미승인 시 색 없이 텍스트·아이콘으로 표시 |
| 동시 세션 초과 정책 | 확인 필요 | 가장 오래된 세션 만료(`maxSessionsPreventsLogin=false`) 권장. 새 로그인 차단을 원하면 값만 변경 |
| 기본 Space 이름 | 결정 | `"{displayName}의 Space"`, 120자 제한. 첫 로그인 후 설정에서 변경 안내 |
| M1 계약 전환 시점 | 결정 | 컨트롤러·마이그레이션 완료 후 `publish` 모드로 `docs/generated/` 갱신. 그 전까지 초안 유지 |

- `AuthPrincipal` 필드가 늘어나면 `serialVersionUID`를 유지하고, 배포 시 세션을 강제 만료할지 결정해야 합니다. M1은 배포 전 세션 테이블을 비우는 것을 권장합니다.
- 메일 본문 링크는 `PUBLIC_BASE_URL`을 사용합니다. 잘못된 값은 재설정 불가로 이어지므로 non-local에서 HTTPS를 검증합니다.

## 12. 다음 문서

M2는 Room·Shelf·Box 계층, 이동, `effectivePermission`을 다룹니다. 상세 계획은 [PLAN-003 계층과 권한](PLAN-003-hierarchy-permissions.md)이며, 범위와 완료 조건은 [구현 로드맵의 M2](../implementation-roadmap.md#m2-계층과-권한-기반)를 따릅니다. M1의 `AuthPrincipal`, 세션, 테스트 픽스처, 교차 Space 검증 기반을 M2가 재사용합니다.


## 13. 구현 편차와 이월

| 계획 | 실제 구현 | 이유 |
|---|---|---|
| `SignupService`·`LoginService`·`SpaceService` 분리 | `AuthService` 단일 서비스 + `SessionService` | M1 범위가 작고 트랜잭션 경계가 단순해 중복 분리를 피함 |
| `AuthPrincipal` | `AppUserPrincipal` (`Serializable`, username=userId) | 세션 직렬화와 `principal_name` 인덱스 안정성 |
| Thymeleaf 메일 템플릿 | `SmtpMailPort` HTML·텍스트 문자열 | 의존성과 템플릿 로딩을 줄이고 Mailpit으로 검증 |
| `docs_generate.sh publish` | 미리보기 유지 | M2~M8 계약 초안 보존 |
| GreenMail 통합 | `FakeMailPort` + Mailpit 수동 확인 | M1 테스트 속도와 SMTP 의존 최소화 |
| `application-contract.yml` | `test` 프로필 + Testcontainers | 인증 컨트롤러가 있는 실제 컨텍스트 OpenAPI 생성 |


## 14. M1 검증 결과

| 검증 | 결과 |
|---|---|
| `./gradlew :backend:ktlintCheck :backend:detekt :backend:test` | 통과, 42개 테스트 |
| `pnpm lint`, `pnpm format:check`, `pnpm test` | 통과, 14개 테스트 |
| `pnpm build` | 통과 |
| `pnpm test:e2e` | 데스크톱·모바일 4개 통과 |
| `bash scripts/docs_generate.sh all` | OpenAPI 10 paths, 데이터 사전 8 tables, 이벤트 0 channels |
| `python3 scripts/docs_check.py` | 통과, 41개 문서 |
| 로컬 Mailpit | 재설정 메일 수신 확인 |
| 초대코드 CLI | create 명령으로 코드 생성·가입 확인 |
| 보안 | CSRF 403, 세션 회전, 전체 세션 무효화, 최대 5세션, 422 초대·토큰 오류 확인 |

M1 계약은 미리보기 모드로 검증했고, `docs/generated/`의 M2~M8 설계 초안은 보존했습니다.
