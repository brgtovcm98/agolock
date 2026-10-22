"""구현된 OpenAPI path가 계약 문서에 존재하는지 검사합니다.

`build/contracts/openapi.yaml`(springdoc 미리보기)의 path/method가
`docs/generated/openapi.yaml`에 없으면 실패합니다. 계약 문서는 `/api/v1`
server prefix를 쓰므로 path를 정규화해 비교합니다.
"""

from __future__ import annotations

import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "build" / "contracts" / "openapi.yaml"
PREVIEW_JSON = ROOT / "build" / "contracts" / "openapi.json"
CANONICAL = ROOT / "docs" / "generated" / "openapi.yaml"

# 아직 계약 문서에 옮기지 않은 구현 path입니다. 새 구현 path를 추가할 때는
# 여기에 넣지 말고 docs/generated/openapi.yaml에 계약을 먼저 추가합니다.
ALLOWLIST: set[str] = set()


def _normalize(path: str) -> str:
    return path if path.startswith("/api/v1") else f"/api/v1{path}"


def check_openapi_coverage() -> int:
    if not PREVIEW.exists() or not CANONICAL.exists():
        return 0

    preview = yaml.safe_load(PREVIEW.read_text(encoding="utf-8")) or {}
    canonical = yaml.safe_load(CANONICAL.read_text(encoding="utf-8")) or {}
    preview_paths = preview.get("paths", {})

    # normalize_openapi.py를 건너뛰고 오래된 preview.yaml로 통과하는 것을 막는다.
    if PREVIEW_JSON.exists():
        fresh = json.loads(PREVIEW_JSON.read_text(encoding="utf-8"))
        fresh_paths = set(fresh.get("paths", {}))
        if fresh_paths != set(preview_paths):
            print("FAIL: build/contracts/openapi.yaml이 openapi.json과 다릅니다. normalize_openapi.py를 다시 실행하세요.")
            return 1
    canonical_paths = {_normalize(path): ops for path, ops in canonical.get("paths", {}).items()}

    missing = sorted(set(preview_paths) - set(canonical_paths) - ALLOWLIST)
    missing_methods = []
    for path in sorted(set(preview_paths) & set(canonical_paths)):
        methods = set(preview_paths[path]) - set(canonical_paths[path])
        if methods:
            missing_methods.append((path, sorted(methods)))

    if not missing and not missing_methods:
        return 0

    for path in missing:
        print(f"FAIL: OpenAPI 계약에 없는 구현 path: {path}")
    for path, methods in missing_methods:
        print(f"FAIL: OpenAPI 계약에 없는 method: {path} {methods}")
    return len(missing) + len(missing_methods)


if __name__ == "__main__":
    raise SystemExit(1 if check_openapi_coverage() else 0)
