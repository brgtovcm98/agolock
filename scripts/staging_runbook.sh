#!/usr/bin/env bash
# staging 배포 보조 스크립트. 실행 전 반드시 deploy/.env.staging을 검토하세요.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT/deploy/compose.staging.yml"
ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env.staging}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"

usage() {
  cat <<'USAGE'
usage: scripts/staging_runbook.sh <command>

commands:
  preflight   필수 환경 변수와 compose 설정 확인
  backup      PostgreSQL custom-format 백업 생성
  migrate     app 재배포(Flyway V19~V23 적용)
  audit       scripts/audit_data.sql 실행
  smoke       readiness/auth config 확인
  load        k6 실행 안내
USAGE
}

require_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "missing env file: $ENV_FILE" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

preflight() {
  require_env_file
  : "${PUBLIC_BASE_URL:?required}"
  : "${POSTGRES_PASSWORD:?required}"
  : "${RABBITMQ_PASSWORD:?required}"
  : "${S3_SECRET_KEY:?required}"
  : "${S3_PUBLIC_ENDPOINT:?required}"
  : "${NOTIFICATION_SECRET_KEY:?required}"
  echo "required env: ok"
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" config --quiet
  echo "compose config: ok"
}

backup() {
  require_env_file
  mkdir -p "$BACKUP_DIR"
  local stamp
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  local target="$BACKUP_DIR/agolock-staging-$stamp.dump"
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" \
    exec -T postgres pg_dump -U "${POSTGRES_USER:-agolock}" -d "${POSTGRES_DB:-agolock}" -Fc > "$target"
  echo "backup: $target"
}

migrate() {
  require_env_file
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --build app
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps
}

audit() {
  require_env_file
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" \
    exec -T postgres psql -U "${POSTGRES_USER:-agolock}" -d "${POSTGRES_DB:-agolock}" \
    < "$ROOT/scripts/audit_data.sql"
}

smoke() {
  require_env_file
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" \
    exec -T app curl -fsS "http://127.0.0.1:9090/actuator/health/readiness"
  curl -fsS -o /dev/null -w 'auth config: %{http_code}\n' "$PUBLIC_BASE_URL/api/v1/auth/config"
  curl -sS -o /dev/null -w 'info unauth: %{http_code}\n' "$PUBLIC_BASE_URL/api/v1/info"
}

load() {
  cat <<'MSG'
k6는 staging 전용 계정으로 실행하세요.
  BASE_URL=$PUBLIC_BASE_URL TEST_EMAIL=... TEST_PASSWORD=... \
    k6 run -e ... load/k6/inventory-list.js
  EXPLAIN: psql -f scripts/explain_queries.sql
MSG
}

case "${1:-}" in
  preflight) preflight ;;
  backup) backup ;;
  migrate) migrate ;;
  audit) audit ;;
  smoke) smoke ;;
  load) load ;;
  *) usage; exit 1 ;;
esac
