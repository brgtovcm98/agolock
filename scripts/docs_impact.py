#!/usr/bin/env python3
"""변경된 코드 경로에 영향을 받는 문서를 찾습니다.

사용 예:
    python3 scripts/docs_impact.py
    python3 scripts/docs_impact.py backend/src/main/kotlin/.../InventoryService.kt
    git diff --name-only | python3 scripts/docs_impact.py -
"""

from __future__ import annotations

import fnmatch
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
INDEX = ROOT / "docs" / "index.yaml"


def load_docs() -> list[dict]:
    data = yaml.safe_load(INDEX.read_text(encoding="utf-8"))
    return data.get("docs", [])


def changed_paths() -> list[str]:
    if len(sys.argv) > 1:
        if sys.argv[1] == "-":
            return [line.strip() for line in sys.stdin if line.strip()]
        return [arg for arg in sys.argv[1:] if arg.strip()]

    try:
        result = subprocess.run(
            ["git", "diff", "--name-only", "HEAD"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        return [line.strip() for line in result.stdout.splitlines() if line.strip()]
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("변경 경로를 찾지 못했습니다. 경로를 인자로 넘기거나 git 저장소에서 실행하세요.")
        raise SystemExit(2)


def matches(path: str, patterns: list[str]) -> bool:
    normalized = path.replace("\\", "/")
    return any(fnmatch.fnmatch(normalized, pattern.replace("\\", "/")) for pattern in patterns)


def main() -> int:
    docs = load_docs()
    paths = changed_paths()
    if not paths:
        print("변경된 파일이 없습니다.")
        return 0

    affected: list[tuple[dict, list[str]]] = []
    for doc in docs:
        patterns = doc.get("code_paths") or []
        if not patterns:
            continue
        matched = [path for path in paths if matches(path, patterns)]
        if matched:
            affected.append((doc, matched))

    if not affected:
        print("영향받는 문서가 없습니다.")
        return 0

    print("영향받는 문서:")
    for doc, matched in affected:
        generated = " [generated]" if doc.get("generated") else ""
        print(f"- {doc['id']}: {doc['path']}{generated}")
        print(f"  summary: {doc.get('summary', '')}")
        for path in matched:
            print(f"  changed: {path}")

    print("\n갱신 후 python3 scripts/docs_check.py를 실행하세요.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
