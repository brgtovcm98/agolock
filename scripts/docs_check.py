#!/usr/bin/env python3
"""문서 경로, YAML, 상대 링크, 참조, 계약, 단원 기호를 검사합니다."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

from contract_validation import (
    resolve_ref,
    validate_asyncapi,
    validate_openapi,
    walk_refs,
)

ROOT = Path(__file__).resolve().parent.parent
INDEX = ROOT / "docs" / "index.yaml"
LINK_RE = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
SECTION_SYMBOL = "\u00a7"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
PREVIEW_DIR = ROOT / "build" / "contracts"


def markdown_files() -> list[Path]:
    files = [ROOT / "README.md", ROOT / "AGENTS.md"]
    files.extend(sorted((ROOT / "docs").rglob("*.md")))
    return [path for path in files if path.exists()]


def fail(message: str) -> None:
    print(f"FAIL: {message}")


def check_index() -> tuple[list[dict], int]:
    errors = 0
    if not INDEX.exists():
        fail(f"인덱스가 없습니다: {INDEX}")
        return [], 1

    try:
        data = yaml.safe_load(INDEX.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        fail(f"인덱스 YAML 파싱 실패: {exc}")
        return [], 1

    docs = data.get("docs", [])
    ids: set[str] = set()
    for doc in docs:
        doc_id = doc.get("id")
        path = doc.get("path")
        if not doc_id:
            fail("문서 ID가 없습니다.")
            errors += 1
            continue
        if doc_id in ids:
            fail(f"중복 문서 ID: {doc_id}")
            errors += 1
        ids.add(doc_id)

        if not path:
            fail(f"{doc_id}: path가 없습니다.")
            errors += 1
            continue
        if not (ROOT / path).exists():
            fail(f"{doc_id}: 파일이 없습니다: {path}")
            errors += 1

        last_verified = str(doc.get("last_verified_commit", ""))
        if last_verified != "pending" and not SHA_RE.fullmatch(last_verified):
            fail(f"{doc_id}: last_verified_commit이 pending 또는 40자 SHA가 아닙니다: {last_verified}")
            errors += 1
    return docs, errors


def check_yaml_files() -> int:
    errors = 0
    for path in sorted((ROOT / "docs").rglob("*.yaml")):
        try:
            data = yaml.safe_load(path.read_text(encoding="utf-8"))
        except yaml.YAMLError as exc:
            fail(f"YAML 파싱 실패: {path.relative_to(ROOT)}: {exc}")
            errors += 1
            continue

        for location, ref in walk_refs(data):
            try:
                resolve_ref(data, ref)
            except (KeyError, IndexError, ValueError, TypeError):
                fail(f"깨진 참조: {path.relative_to(ROOT)} -> {ref} ({location})")
                errors += 1
    return errors


def check_openapi(path: Path, *, preview: bool) -> int:
    if not path.exists():
        if preview:
            print(f"INFO: 아직 계약 미리보기가 없습니다: {path.relative_to(ROOT)}")
            return 0
        fail(f"OpenAPI 문서가 없습니다: {path.relative_to(ROOT)}")
        return 1

    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        fail(f"OpenAPI YAML 파싱 실패: {path.relative_to(ROOT)}: {exc}")
        return 1

    header = path.read_text(encoding="utf-8").splitlines()[:3]
    expected = "생성 미리보기" if preview else "설계 초안"
    if not any(expected in line for line in header):
        fail(f"{path.relative_to(ROOT)}: 머리말에 '{expected}' 표시가 없습니다.")
        return 1

    errors = validate_openapi(data)
    for error in errors:
        fail(f"{path.relative_to(ROOT)}: {error}")
    return len(errors)


def check_asyncapi(path: Path, *, preview: bool) -> int:
    if not path.exists():
        if preview:
            print(f"INFO: 아직 이벤트 미리보기가 없습니다: {path.relative_to(ROOT)}")
            return 0
        fail(f"이벤트 계약이 없습니다: {path.relative_to(ROOT)}")
        return 1

    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        fail(f"이벤트 YAML 파싱 실패: {path.relative_to(ROOT)}: {exc}")
        return 1

    header = path.read_text(encoding="utf-8").splitlines()[:3]
    expected = "생성 미리보기" if preview else "설계 초안"
    if not any(expected in line for line in header):
        fail(f"{path.relative_to(ROOT)}: 머리말에 '{expected}' 표시가 없습니다.")
        return 1

    errors = validate_asyncapi(data)
    for error in errors:
        fail(f"{path.relative_to(ROOT)}: {error}")
    return len(errors)


def check_data_dictionary(path: Path, *, preview: bool) -> int:
    if not path.exists():
        if preview:
            print(f"INFO: 아직 데이터 사전 미리보기가 없습니다: {path.relative_to(ROOT)}")
            return 0
        fail(f"데이터 사전이 없습니다: {path.relative_to(ROOT)}")
        return 1

    text = path.read_text(encoding="utf-8")
    expected = "> 상태: 생성 미리보기" if preview else "> 상태: 설계 초안"
    if expected not in text:
        fail(f"{path.relative_to(ROOT)}: '{expected}' 표시가 없습니다.")
        return 1
    return 0


def check_contracts() -> int:
    errors = 0
    errors += check_openapi(ROOT / "docs" / "generated" / "openapi.yaml", preview=False)
    errors += check_asyncapi(ROOT / "docs" / "generated" / "events.yaml", preview=False)
    errors += check_data_dictionary(ROOT / "docs" / "generated" / "data-dictionary.md", preview=False)

    errors += check_openapi(PREVIEW_DIR / "openapi.yaml", preview=True)
    errors += check_asyncapi(PREVIEW_DIR / "events.yaml", preview=True)
    errors += check_data_dictionary(PREVIEW_DIR / "data-dictionary.md", preview=True)
    return errors


def check_links() -> int:
    errors = 0
    for path in markdown_files():
        text = path.read_text(encoding="utf-8")
        for raw in LINK_RE.findall(text):
            target = raw.strip().split("#", 1)[0]
            if not target:
                continue
            if target.startswith(("http://", "https://", "mailto:", "tel:")):
                continue
            resolved = (path.parent / target).resolve()
            if not resolved.exists():
                fail(f"깨진 링크: {path.relative_to(ROOT)} -> {raw}")
                errors += 1
    return errors


def check_text_rules() -> int:
    errors = 0
    for path in markdown_files():
        text = path.read_text(encoding="utf-8")
        if SECTION_SYMBOL in text:
            fail(f"단원 기호가 남아 있습니다: {path.relative_to(ROOT)}")
            errors += 1
    return errors


def main() -> int:
    docs, errors = check_index()
    errors += check_yaml_files()
    errors += check_contracts()
    errors += check_links()
    errors += check_text_rules()

    if errors:
        print(f"\n문서 검사 실패: {errors}개 오류")
        return 1

    print(
        f"문서 검사 통과: {len(docs)}개 인덱스 문서, "
        f"{len(markdown_files())}개 Markdown, 계약 검사 포함"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
