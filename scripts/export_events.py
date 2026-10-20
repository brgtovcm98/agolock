#!/usr/bin/env python3
"""백엔드 AsyncAPI 원본을 검증하고 이벤트 계약 미리보기로 복사합니다."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml

from contract_validation import validate_asyncapi

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_INPUT = ROOT / "backend" / "src" / "main" / "resources" / "asyncapi" / "events.yaml"
DEFAULT_OUTPUT = ROOT / "build" / "contracts" / "events.yaml"
HEADER = """# AGO Lock 비동기 이벤트 계약 (생성 미리보기)
# M0에서는 docs/generated/events.yaml의 상세 설계 초안을 보존합니다.
# 백엔드 AsyncAPI 원본을 검증해 미리보기로 복사하며, 실제 generated 기록은 M1부터 시작합니다.
# 이 파일은 직접 수정하지 않습니다.
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    if not args.input.exists():
        print(f"FAIL: AsyncAPI 원본이 없습니다: {args.input}")
        return 1

    document = yaml.safe_load(args.input.read_text(encoding="utf-8"))
    errors = validate_asyncapi(document)
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

    channel_count = len(document.get("channels", {}))
    print(f"이벤트 미리보기 생성: {args.output.relative_to(ROOT)} ({channel_count} channels)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
