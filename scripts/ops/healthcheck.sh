#!/usr/bin/env bash
set -euo pipefail

base_url="${1:?usage: healthcheck.sh https://staging.example.com}"
for path in /actuator/health/liveness /actuator/health/readiness; do
  status="$(curl -fsS -o /dev/null -w '%{http_code}' "${base_url}${path}")"
  echo "${path} ${status}"
  test "${status}" = "200"
done
