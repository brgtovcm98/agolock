#!/usr/bin/env python3
"""PostgreSQL 스키마에서 데이터 사전 미리보기를 생성합니다."""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT = ROOT / "build" / "contracts" / "data-dictionary.md"
DEFAULT_DATABASE_URL = "postgresql://agolock:agolock@localhost:5432/agolock"
HEADER = """# AGO Lock 데이터 사전 (생성 미리보기)

> 상태: 생성 미리보기
> 역할: M0 데이터베이스 스키마의 introspection 결과
> 주의: 이 파일은 `docs/generated/data-dictionary.md`의 상세 설계 초안을 덮어쓰지 않습니다. 실제 generated 기록은 M1부터 시작합니다.
"""


def normalize_database_url(raw: str) -> str:
    value = raw.strip()
    if value.startswith("jdbc:"):
        value = value[len("jdbc:") :]
    if value.startswith("postgres://"):
        value = "postgresql://" + value[len("postgres://") :]
    return value


def masked_url(raw: str) -> str:
    try:
        parts = urlsplit(raw)
    except ValueError:
        return raw
    if not parts.password:
        return raw
    netloc = f"{parts.username}:***@{parts.hostname}"
    if parts.port:
        netloc += f":{parts.port}"
    return urlunsplit((parts.scheme, netloc, parts.path, parts.query, parts.fragment))


def cell(value: object) -> str:
    if value is None:
        return ""
    return str(value).replace("|", "\\|").replace("\n", " ").strip()


def render_type(row: dict) -> str:
    data_type = row["data_type"]
    udt_name = row["udt_name"]
    if data_type == "character varying" and row["character_maximum_length"]:
        return f"varchar({row['character_maximum_length']})"
    if data_type == "numeric" and row["numeric_precision"]:
        scale = row["numeric_scale"] or 0
        return f"numeric({row['numeric_precision']},{scale})"
    if data_type == "USER-DEFINED":
        return udt_name
    return data_type


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--database-url",
        default=os.environ.get("DATABASE_URL", DEFAULT_DATABASE_URL),
        help="PostgreSQL 접속 URL. JDBC 형식도 허용합니다.",
    )
    args = parser.parse_args()

    try:
        import psycopg
        from psycopg.rows import dict_row
    except ImportError:
        print("FAIL: psycopg가 필요합니다. python3 -m pip install -r scripts/requirements.txt")
        return 1

    database_url = normalize_database_url(args.database_url)
    lines = [HEADER, "", f"- DB: `{masked_url(database_url)}`", ""]

    try:
        with psycopg.connect(database_url, connect_timeout=5, row_factory=dict_row) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SELECT extname, extversion FROM pg_extension ORDER BY extname"
                )
                extensions = cursor.fetchall()

                cursor.execute(
                    """
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                    ORDER BY table_name
                    """
                )
                tables = [row["table_name"] for row in cursor.fetchall()]

                cursor.execute(
                    """
                    SELECT t.typname, string_agg(e.enumlabel, ', ' ORDER BY e.enumsortorder) AS labels
                    FROM pg_type t
                    JOIN pg_enum e ON t.oid = e.enumtypid
                    JOIN pg_namespace n ON n.oid = t.typnamespace
                    WHERE n.nspname = 'public'
                    GROUP BY t.typname
                    ORDER BY t.typname
                    """
                )
                enums = cursor.fetchall()

                lines.extend(["## 1. 확장", "", "| 확장 | 버전 |", "|---|---|"])
                for extension in extensions:
                    lines.append(f"| `{cell(extension['extname'])}` | {cell(extension['extversion'])} |")
                if not extensions:
                    lines.append("| 없음 | |")
                lines.append("")

                lines.extend(["## 2. Enum", "", "| Enum | 값 |", "|---|---|"])
                for enum in enums:
                    lines.append(f"| `{cell(enum['typname'])}` | {cell(enum['labels'])} |")
                if not enums:
                    lines.append("| 없음 | |")
                lines.append("")

                lines.extend(["## 3. 테이블", ""])
                if not tables:
                    lines.append("아직 생성된 테이블이 없습니다.")
                    lines.append("")
                for table in tables:
                    lines.extend([f"### `public.{table}`", ""])
                    cursor.execute(
                        """
                        SELECT column_name, data_type, udt_name, is_nullable, column_default,
                               character_maximum_length, numeric_precision, numeric_scale
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = %s
                        ORDER BY ordinal_position
                        """,
                        (table,),
                    )
                    columns = cursor.fetchall()
                    lines.extend(
                        [
                            "| 컬럼 | 타입 | NULL | 기본값 |",
                            "|---|---|---|---|",
                        ]
                    )
                    for column in columns:
                        nullable = "YES" if column["is_nullable"] == "YES" else "NO"
                        lines.append(
                            f"| `{cell(column['column_name'])}` | {cell(render_type(column))} | "
                            f"{nullable} | {cell(column['column_default'])} |"
                        )
                    lines.append("")

                    cursor.execute(
                        """
                        SELECT c.conname, c.contype, pg_get_constraintdef(c.oid) AS definition
                        FROM pg_constraint c
                        JOIN pg_class t ON t.oid = c.conrelid
                        JOIN pg_namespace n ON n.oid = t.relnamespace
                        WHERE n.nspname = 'public' AND t.relname = %s
                        ORDER BY c.conname
                        """,
                        (table,),
                    )
                    constraints = cursor.fetchall()
                    lines.extend(["**제약**", "", "| 이름 | 종류 | 정의 |", "|---|---|---|"])
                    for constraint in constraints:
                        lines.append(
                            f"| `{cell(constraint['conname'])}` | {cell(constraint['contype'])} | "
                            f"{cell(constraint['definition'])} |"
                        )
                    if not constraints:
                        lines.append("| 없음 | | |")
                    lines.append("")

                    cursor.execute(
                        """
                        SELECT indexname, indexdef
                        FROM pg_indexes
                        WHERE schemaname = 'public' AND tablename = %s
                        ORDER BY indexname
                        """,
                        (table,),
                    )
                    indexes = cursor.fetchall()
                    lines.extend(["**인덱스**", "", "| 이름 | 정의 |", "|---|---|"])
                    for index in indexes:
                        lines.append(
                            f"| `{cell(index['indexname'])}` | {cell(index['indexdef'])} |"
                        )
                    if not indexes:
                        lines.append("| 없음 | |")
                    lines.append("")

    except Exception as error:  # noqa: BLE001 - CLI는 원인을 짧게 보여 주고 실패합니다.
        print(f"FAIL: 데이터베이스 introspection 실패: {error}")
        print("docker compose -f infra/docker-compose.yml up -d postgres 후 다시 실행하세요.")
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(f"데이터 사전 미리보기 생성: {args.output.relative_to(ROOT)} ({len(tables)} tables)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
