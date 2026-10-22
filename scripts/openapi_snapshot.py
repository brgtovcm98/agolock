"""생성된 OpenAPI 스키마가 승인된 snapshot과 같은지 검사합니다.

path/method coverage(`openapi_coverage.py`)와 달리 request/response schema,
required, enum, example까지 비교합니다. 의도한 계약 변경이면
`python3 scripts/openapi_snapshot.py --update`로 snapshot을 갱신합니다.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "build" / "contracts" / "openapi.json"
SNAPSHOT = ROOT / "contracts" / "openapi.snapshot.json"


def _diff(
    actual: Any,
    expected: Any,
    path: str = "",
    out: list[str] | None = None,
) -> list[str]:
    out = out if out is not None else []
    if len(out) >= 20:
        return out
    if type(actual) is not type(expected):
        out.append(f"{path or '/'}: type {type(actual).__name__} != {type(expected).__name__}")
        return out
    if isinstance(actual, dict):
        for key in sorted(set(actual) | set(expected)):
            if key not in actual:
                out.append(f"{path}/{key}: snapshot에만 있음")
            elif key not in expected:
                out.append(f"{path}/{key}: 구현에만 있음")
            else:
                _diff(actual[key], expected[key], f"{path}/{key}", out)
    elif isinstance(actual, list):
        if len(actual) != len(expected):
            out.append(f"{path or '/'}: array length {len(actual)} != {len(expected)}")
        for index, (a, b) in enumerate(zip(actual, expected)):
            _diff(a, b, f"{path}[{index}]", out)
    elif actual != expected:
        out.append(f"{path or '/'}: {actual!r} != {expected!r}")
    return out


def check() -> int:
    if not PREVIEW.exists() or not SNAPSHOT.exists():
        return 0
    actual = json.loads(PREVIEW.read_text(encoding="utf-8"))
    expected = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
    differences = _diff(actual, expected)
    if not differences:
        return 0
    print("FAIL: OpenAPI schema snapshot이 변경되었습니다.")
    for line in differences:
        print(f"  {line}")
    print("의도한 변경이면 python3 scripts/openapi_snapshot.py --update 로 갱신하세요.")
    return 1


def update() -> int:
    if not PREVIEW.exists():
        print("build/contracts/openapi.json이 없습니다. generateOpenApiPreview를 먼저 실행하세요.")
        return 1
    data = json.loads(PREVIEW.read_text(encoding="utf-8"))
    SNAPSHOT.parent.mkdir(parents=True, exist_ok=True)
    SNAPSHOT.write_text(
        json.dumps(data, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"updated {SNAPSHOT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(update() if "--update" in sys.argv else 1 if check() else 0)
