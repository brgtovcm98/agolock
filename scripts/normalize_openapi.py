#!/usr/bin/env python3
"""springdoc가 만든 OpenAPI JSON을 검증하고 YAML 미리보기로 정규화합니다."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import yaml

from contract_validation import validate_openapi

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_INPUT = ROOT / "build" / "contracts" / "openapi.json"
DEFAULT_OUTPUT = ROOT / "build" / "contracts" / "openapi.yaml"
HEADER = """# AGO Lock HTTP API 계약 (생성 미리보기)
# M0에서는 docs/generated/openapi.yaml의 상세 설계 초안을 보존합니다.
# 구현된 컨트롤러만 이 미리보기에 생성하며, 실제 generated 기록은 M1부터 시작합니다.
# 이 파일은 직접 수정하지 않습니다.
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    if not args.input.exists():
        print(f"FAIL: OpenAPI JSON이 없습니다: {args.input}")
        print("먼저 ./gradlew :backend:generateOpenApiPreview를 실행하세요.")
        return 1

    document = json.loads(args.input.read_text(encoding="utf-8"))
    errors = validate_openapi(document)
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    body = yaml.safe_dump(
        document,
        allow_unicode=True,
        sort_keys=False,
        default_flow_style=False,
        width=120,
    )
    args.output.write_text(HEADER + body, encoding="utf-8")

    path_count = len(document.get("paths", {}))
    print(f"OpenAPI 미리보기 생성: {args.output.relative_to(ROOT)} ({path_count} paths)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
