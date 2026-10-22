# 로컬 실행 Runbook

> 상태: 확정
> 역할: 로컬 개발 환경을 처음부터 띄우고, API·UI·이미지 저장소까지 확인하는 절차
> 관련 문서: [운영과 검증](operations.md), [아키텍처](architecture.md), [프론트엔드](frontend.md), [AGENTS.md](../AGENTS.md)

## 1. 목적과 범위

이 문서는 개발자가 로컬에서 다음을 재현하는 절차를 다룹니다.

- Docker Compose로 PostgreSQL, RabbitMQ, MinIO, Mailpit을 실행한다.
- Spring Boot API를 local 프로필로 실행한다.
- 필요하면 worker를 추가로 실행해 이미지 처리와 outbox를 확인한다.
- Vite 개발 서버를 실행하고 실제 브라우저에서 로그인·검색·재고 화면을 확인한다.
- 테스트, lint, 문서 검사를 실행한다.

운영·staging 배포는 [운영과 검증](operations.md)을 따릅니다. 이 문서의 자격증명과 포트는 로컬 전용입니다.

## 2. 구성과 기본 포트

| 구성 | 기본 포트 | 비고 |
|---|---:|---|
| PostgreSQL + pgvector | 5432 | 데이터는 Docker volume에 유지 |
| RabbitMQ | 5672 | management 15672 |
| MinIO API | 9000 | console 9001 |
| Mailpit SMTP | 1025 | UI 8025 |
| Spring Boot API | 8080 | management 9090 |
| Vite 개발 서버 | 5173 | `/api`를 API로 proxy |

> 8080이나 5173이 이미 사용 중이면 8081/9091, 5174처럼 다른 포트를 사용합니다. 프론트엔드는 `VITE_API_TARGET`으로 API 주소를 바꿀 수 있습니다.

## 3. 사전 준비

필수 도구는 다음과 같습니다.

- Docker Engine과 Docker Compose
- JDK 21
- Node.js 22 계열과 pnpm 10 계열
- Python 3와 `scripts/requirements.txt` 패키지(docs 검사용)

저장소에는 검증된 도구를 `.tools/`에 설치하는 스크립트가 있습니다.

```bash
bash scripts/install-tools.sh
source scripts/env.sh
java -version
node --version
pnpm --version
```

`source scripts/env.sh`는 `JAVA_HOME`, `PATH`, `GRADLE_USER_HOME`, Playwright 브라우저 경로를 현재 셸에 설정합니다. 새 터미널을 열 때마다 다시 실행합니다.

## 4. 환경 변수

기본 local 프로필은 `backend/src/main/resources/application-local.yml`에 로컬 기본값을 가지고 있습니다. 별도 설정이 필요할 때만 `.env.local`을 만듭니다.

```bash
cp .env.local.example .env.local
set -a
source .env.local
set +a
```

- `.env.local`은 커밋하지 않습니다.
- `NOTIFICATION_SECRET_KEY`는 base64 32바이트여야 합니다. local 프로필에는 검증용 기본 키가 있으므로 로컬에서는 생략할 수 있습니다.
- staging/production의 키를 로컬로 복사하지 않습니다.
- `AUTH_SIGNUP_INVITE_REQUIRED=true`이면 초대코드 없이 가입할 수 없습니다. 로컬에서 자유 가입이 필요하면 실행 시 `AUTH_SIGNUP_INVITE_REQUIRED=false`를 지정합니다.

## 5. 1회 부트스트랩

### 5.1 인프라 실행

```bash
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

다음 서비스가 `healthy` 또는 실행 중이어야 합니다.

- `agolock-postgres-1`
- `agolock-rabbitmq-1`
- `agolock-minio-1`
- `agolock-mailpit-1`

MinIO 버킷은 `minio-init`이 자동 생성합니다. 버킷이 없거나 초기화가 실패하면 다음으로 확인합니다.

```bash
docker compose -f infra/docker-compose.yml logs minio-init
docker compose -f infra/docker-compose.yml exec -T minio \
  mc --config-dir /tmp/.mc alias set local http://127.0.0.1:9000 agolock agolock-local-secret
docker compose -f infra/docker-compose.yml exec -T minio \
  mc --config-dir /tmp/.mc ls local
```

`agolock-local/`이 보이면 정상입니다. 없으면 `infra/minio/init.sh`를 참고해 버킷을 수동 생성합니다.

### 5.2 백엔드 실행

기본 포트를 사용하는 경우:

```bash
./gradlew :backend:bootRun \
  --args='--spring.profiles.active=local'
```

로컬 가입을 열고 포트를 바꾸는 경우:

```bash
AUTH_SIGNUP_INVITE_REQUIRED=false \
  ./gradlew :backend:bootRun \
  --args='--spring.profiles.active=local --server.port=8081 --management.server.port=9091'
```

기동 확인:

```bash
curl -fsS http://127.0.0.1:8080/api/v1/auth/config
curl -fsS http://127.0.0.1:9090/actuator/health/readiness
```

`{"status":"UP"}`가 나오면 API와 DB 연결이 정상입니다. local 프로필에서는 Swagger UI도 활성화됩니다.

- API: `http://127.0.0.1:8080`
- Swagger UI: `http://127.0.0.1:8080/swagger-ui.html`
- readiness: `http://127.0.0.1:9090/actuator/health/readiness`

### 5.3 worker 실행(선택)

이미지 처리, outbox relay, 정리 job까지 확인하려면 별도 터미널에서 worker를 실행합니다.

```bash
./gradlew :backend:bootRun \
  --args='--spring.profiles.active=local,worker'
```

- API와 worker는 같은 DB와 RabbitMQ를 사용합니다.
- 이미지 업로드 후 `image.uploaded` 이벤트가 worker에서 처리됩니다.
- worker를 띄우지 않으면 이미지가 `PENDING`에 머물 수 있습니다.
- worker는 `web-application-type=none`이므로 API의 HTTP 포트와 충돌하지 않습니다.

### 5.4 프론트엔드 실행

```bash
cd frontend
pnpm install
pnpm dev
```

API가 8080이 아니면 proxy target을 지정합니다.

```bash
VITE_API_TARGET=http://127.0.0.1:8081 pnpm exec vite --host 127.0.0.1 --port 5174
```

- UI: `http://127.0.0.1:5173` (또는 지정한 포트)
- `/api`와 `/actuator` 요청은 `VITE_API_TARGET`으로 전달됩니다.
- 기본값은 `http://localhost:8080`입니다.

## 6. 동작 확인

### 6.1 API 최소 확인

```bash
# 인증 설정
curl -fsS http://127.0.0.1:8080/api/v1/auth/config

# readiness
curl -fsS http://127.0.0.1:9090/actuator/health/readiness
```

브라우저에서 UI를 열고 다음 순서로 확인합니다.

1. 회원가입 또는 로그인
2. Room 생성
3. Item 생성
4. Item에 Inventory 생성
5. 홈·검색에서 Item과 위치 확인
6. Inventory 상세에서 권한(`OWNER`)과 위치 확인
7. 이미지 presign이 필요하면 Item/Inventory 상세에서 업로드 URL 발급 확인

### 6.2 이미지 저장소 확인

presign 응답의 `uploadUrl`로 실제 PUT을 수행한 뒤 complete를 호출합니다. worker가 실행 중이면 이미지 상태가 `PENDING` → `PROCESSING` → `READY`로 전환됩니다.

local MinIO는 `http://localhost:9000`, 버킷은 `agolock-local`입니다. 콘솔은 `http://localhost:9001`에서 확인합니다.

### 6.3 메일 확인

초대·비밀번호 재설정 메일은 Mailpit에서 확인합니다.

- SMTP: `localhost:1025`
- UI: `http://localhost:8025`

## 7. 테스트와 품질 검사

```bash
# 백엔드
./gradlew :backend:test
./gradlew :backend:ktlintCheck :backend:detekt

# 문서
python3 scripts/docs_check.py

# 프론트엔드
cd frontend
pnpm lint
pnpm test
pnpm build
```

OpenAPI 계약을 변경했다면 다음 순서로 preview와 snapshot을 갱신합니다.

```bash
./gradlew :backend:generateOpenApiPreview
python3 scripts/normalize_openapi.py
python3 scripts/openapi_snapshot.py --update
python3 scripts/docs_check.py
```

## 8. 종료와 초기화

```bash
# 컨테이너만 중지(볼륨 유지)
docker compose -f infra/docker-compose.yml down

# 로컬 데이터까지 초기화(파괴적)
docker compose -f infra/docker-compose.yml down -v
```

`down -v`는 PostgreSQL, RabbitMQ, MinIO 볼륨을 삭제합니다. 로컬에서만 사용하고, 필요한 데이터가 있으면 먼저 백업합니다.

## 9. 문제 해결

| 증상 | 확인 | 조치 |
|---|---|---|
| `Port 8080 was already in use` | `ss -ltnp '( sport = :8080 )'` | 기존 프로세스를 종료하거나 8081/9091 사용 |
| 프론트 API가 404/502 | Vite proxy target | `VITE_API_TARGET`이 실제 API 포트인지 확인 |
| `NOTIFICATION_SECRET_KEY` 기동 실패 | key 길이 | `openssl rand -base64 32`로 32바이트 키 생성 |
| 가입이 403 | `auth/config`의 `inviteRequired` | 로컬 실행 시 `AUTH_SIGNUP_INVITE_REQUIRED=false` |
| MinIO 버킷 없음 | `minio-init` 로그 | `infra/minio/init.sh` 수동 실행 또는 버킷 생성 |
| 이미지가 `PENDING` | worker 실행 여부 | `local,worker` 프로필로 worker 실행 |
| 로그인 429 | rate limit | 잠시 기다리거나 로컬 DB의 rate limit 상태 확인 |
| Flyway checksum 실패 | 마이그레이션 변경 여부 | 마이그레이션을 수정하지 말고 새 버전 추가, 로컬은 볼륨 초기화 가능 |

## 10. 보안 주의

- 로컬 자격증명은 운영에 사용하지 않습니다.
- `.env.local`과 실제 키를 커밋하지 않습니다.
- MinIO root 계정, Mailpit, RabbitMQ management를 외부 네트워크에 노출하지 않습니다.
- 로컬은 TLS를 사용하지 않으므로 `SESSION_COOKIE_SECURE=true`를 사용하지 않습니다.
- staging/production의 `NOTIFICATION_SECRET_KEY`, S3 키, DB 비밀번호를 로컬로 복사하지 않습니다.
