"""구현된 OpenAPI path가 계약 문서에 존재하는지 검사합니다.

`build/contracts/openapi.yaml`(springdoc 미리보기)의 path/method가
`docs/generated/openapi.yaml`에 없으면 실패합니다. 계약 문서는 `/api/v1`
server prefix를 쓰므로 path를 정규화해 비교합니다.
"""

from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "build" / "contracts" / "openapi.yaml"
CANONICAL = ROOT / "docs" / "generated" / "openapi.yaml"

# 아직 계약 문서에 옮기지 않은 구현 path입니다. 새 구현 path를 추가할 때는
# 여기에 넣지 말고 docs/generated/openapi.yaml에 계약을 먼저 추가합니다.
ALLOWLIST = {
    "/api/v1/info",
    "/api/v1/share-invitations/{invitationId}/resend",
    "/api/v1/shared/boxes/{boxId}",
    "/api/v1/shared/inventories/{inventoryId}",
    "/api/v1/shared/rooms/{roomId}",
    "/api/v1/shared/shelves/{shelfId}",
}


def _normalize(path: str) -> str:
    return path if path.startswith("/api/v1") else f"/api/v1{path}"


def check_openapi_coverage() -> int:
    if not PREVIEW.exists() or not CANONICAL.exists():
        return 0

    preview = yaml.safe_load(PREVIEW.read_text(encoding="utf-8")) or {}
    canonical = yaml.safe_load(CANONICAL.read_text(encoding="utf-8")) or {}
    preview_paths = preview.get("paths", {})
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
