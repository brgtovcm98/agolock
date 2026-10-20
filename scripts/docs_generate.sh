#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

usage() {
  cat <<'EOF'
사용법: bash scripts/docs_generate.sh <openapi|schema|events|all|check>

M0는 미리보기 모드입니다. 결과는 build/contracts/에만 쓰고
docs/generated/의 상세 설계 초안은 덮어쓰지 않습니다.
EOF
}

run_openapi() {
  ./gradlew :backend:generateOpenApiPreview
  python3 scripts/normalize_openapi.py
}

run_schema() {
  python3 scripts/generate_data_dictionary.py
}

run_events() {
  python3 scripts/export_events.py
}

case "${1:-all}" in
  openapi)
    run_openapi
    ;;
  schema)
    run_schema
    ;;
  events)
    run_events
    ;;
  all)
    run_openapi
    run_schema
    run_events
    ;;
  check)
    python3 scripts/docs_check.py
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage
    exit 2
    ;;
esac
