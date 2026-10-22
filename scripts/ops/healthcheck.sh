#!/usr/bin/env bash
set -euo pipefail

# Actuator는 관리 포트(9090)에 있고 nginx/공개 URL에는 게시하지 않는다.
# 따라서 호스트가 아니라 app 컨테이너 내부에서 확인한다.
compose_file="${COMPOSE_FILE:-deploy/compose.staging.yml}"
env_file="${ENV_FILE:-deploy/.env.staging}"

if [[ ! -f "$env_file" ]]; then
  echo "missing env file: $env_file" >&2
  exit 1
fi

for path in /actuator/health/liveness /actuator/health/readiness; do
  docker compose -f "$compose_file" --env-file "$env_file" \
    exec -T app curl -fsS "http://127.0.0.1:9090${path}" >/dev/null
  echo "${path} 200"
done
