#!/usr/bin/env python3
"""구현 OpenAPI에서 물건·재고 계약만 설계 초안에 동기화합니다. 다른 설계 범위는 보존합니다."""
import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent


def main() -> None:
    source = json.loads((ROOT / 'build/contracts/openapi.json').read_text())
    target = ROOT / 'docs/generated/openapi.yaml'
    original = target.read_text()
    document = yaml.safe_load(original)
    schemas = source['components']['schemas']
    pending = []

    def references(value):
        if isinstance(value, dict):
            ref = value.get('$ref', '')
            if ref.startswith('#/components/schemas/'):
                pending.append(ref.rsplit('/', 1)[1])
            for child in value.values():
                references(child)
        elif isinstance(value, list):
            for child in value:
                references(child)

    for path, operations in source['paths'].items():
        local = path.removeprefix('/api/v1')
        if local.startswith(('/items', '/inventories', '/categories')):
            # 이미지 작업의 계약은 이번 동기화 범위에 포함하지 않습니다.
            if '/images' in local:
                continue
            document['paths'][local] = operations
            references(operations)
    copied = set()
    while pending:
        name = pending.pop()
        if name in copied:
            continue
        document['components']['schemas'][name] = schemas[name]
        copied.add(name)
        references(schemas[name])
    header = '\n'.join(line for line in original.splitlines() if line.startswith('#'))
    target.write_text(header + '\n' + yaml.safe_dump(document, allow_unicode=True, sort_keys=False, width=120))
    print(f'물건·재고 계약 동기화: {len(copied)}개 스키마, 설계 초안 표시 유지')


if __name__ == '__main__':
    main()
