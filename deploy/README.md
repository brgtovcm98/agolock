# Staging 배포

이 디렉터리는 1차 릴리스의 **staging 배포 골격**입니다. production은 관리형 PostgreSQL·S3·RabbitMQ·SMTP와 TLS 종단을 사용합니다.

## 구성

- `backend.Dockerfile`: Gradle bootJar 빌드, JDK 21 JRE, non-root, readiness healthcheck
- `frontend.Dockerfile`: pnpm 빌드, nginx 정적 서빙
- `nginx/default.conf`: SPA fallback, same-origin `/api` 프록시, 보안 헤더
- `compose.staging.yml`: app·worker·web·PostgreSQL·RabbitMQ·MinIO·Mailpit
- `../backend/src/main/resources/application-staging.yml`: Secure 쿠키, Swagger 비활성, S3 readiness

## 실행

```bash
cp deploy/.env.staging.example deploy/.env.staging
# 값을 채운 뒤
docker compose -f deploy/compose.staging.yml --env-file deploy/.env.staging up -d --build
docker compose -f deploy/compose.staging.yml --env-file deploy/.env.staging ps
bash scripts/staging_runbook.sh smoke
```

`PUBLIC_BASE_URL`은 실제 HTTPS 주소여야 합니다. staging은 `SESSION_COOKIE_SECURE=true`이므로 TLS 종단(로드밸런서 또는 리버스 프록시) 뒤에서 접속합니다. 로컬에서 HTTP로만 확인하려면 `APP_ENVIRONMENT=local`로 별도 실행하고 staging 프로필을 사용하지 않습니다.

`S3_PUBLIC_ENDPOINT`는 **브라우저에서 접근 가능한 S3 주소**입니다. Compose의 MinIO를 외부에 공개하지 않는다면 관리형 S3 또는 별도 HTTPS 프록시를 사용하고, 이 값을 그 주소로 설정하세요. 서버 내부 접근은 `S3_ENDPOINT`를 사용합니다.

smoke 실행 호스트에는 Docker Compose·curl·Python 3가 필요합니다. 내부 liveness·readiness, 공개 `/healthz`와 인증 설정의 HTTP 200, `inviteRequired`의 Boolean 타입, 비인증 `/api/v1/info`의 HTTP 401을 확인하며, 하나라도 다르면 실패합니다.

## worker

`worker`는 같은 이미지에 `staging,worker` 프로필로 실행됩니다. 웹을 띄우지 않고 이미지 후처리와 정리 job만 수행합니다. Flyway는 API에서만 실행됩니다.

## 배포 순서

1. DB 백업
2. `app` 기동과 Flyway 완료
3. readiness 확인
4. `worker` 기동
5. web·smoke 확인
6. 15분 관측

## 롤백

이전 이미지 태그로 `app`·`worker`를 되돌립니다. 파괴적 마이그레이션은 금지하고 expand-contract를 사용합니다.

## 아직 하지 않은 것

- production 관리형 서비스 연결
- TLS 인증서 자동 갱신
- GitHub Actions에서 실제 서버로 배포하는 단계
- 백업·복구·롤백 리허설
