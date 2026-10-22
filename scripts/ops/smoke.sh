#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-${PUBLIC_BASE_URL:-}}"
if [[ -z "$base_url" ]]; then
  echo "usage: smoke.sh https://staging.example.com (or set PUBLIC_BASE_URL)" >&2
  exit 1
fi

curl -fsS "${base_url}/healthz" >/dev/null
echo "healthz ok"

curl -fsS "${base_url}/api/v1/auth/config" | grep -q 'inviteRequired'
echo "auth config ok"
