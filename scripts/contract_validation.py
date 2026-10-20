"""OpenAPI와 AsyncAPI 문서의 공통 계약 검사를 제공합니다."""

from __future__ import annotations

import re
from typing import Any

HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
PATH_PARAMETER = re.compile(r"\{([^}]+)\}")


def resolve_ref(document: object, ref: str) -> object:
    if not ref.startswith("#/"):
        raise ValueError(f"외부 참조는 지원하지 않습니다: {ref}")
    current: Any = document
    for raw_part in ref[2:].split("/"):
        part = raw_part.replace("~1", "/").replace("~0", "~")
        if isinstance(current, list):
            current = current[int(part)]
        else:
            current = current[part]
    return current


def walk_refs(value: object) -> list[tuple[str, str]]:
    refs: list[tuple[str, str]] = []

    def visit(node: object, location: str) -> None:
        if isinstance(node, dict):
            if isinstance(node.get("$ref"), str):
                refs.append((location, node["$ref"]))
            for key, child in node.items():
                visit(child, f"{location}/{key}")
        elif isinstance(node, list):
            for index, child in enumerate(node):
                visit(child, f"{location}/{index}")

    visit(value, "#")
    return refs


def validate_refs(document: object) -> list[str]:
    errors: list[str] = []
    for location, ref in walk_refs(document):
        try:
            resolve_ref(document, ref)
        except (KeyError, IndexError, ValueError, TypeError):
            errors.append(f"깨진 참조 {ref} ({location})")
    return errors


def _parameter_names(document: object, parameters: list[object]) -> set[str]:
    names: set[str] = set()
    for parameter in parameters:
        if not isinstance(parameter, dict):
            continue
        if isinstance(parameter.get("$ref"), str):
            try:
                parameter = resolve_ref(document, parameter["$ref"])
            except (KeyError, IndexError, ValueError, TypeError):
                continue
        if isinstance(parameter, dict) and isinstance(parameter.get("name"), str):
            names.add(parameter["name"])
    return names


def validate_openapi(document: object) -> list[str]:
    errors: list[str] = []
    if not isinstance(document, dict):
        return ["OpenAPI 문서가 객체가 아닙니다."]

    if not str(document.get("openapi", "")).startswith("3."):
        errors.append("openapi 3.x 버전이 아닙니다.")
    paths = document.get("paths")
    if not isinstance(paths, dict):
        return errors + ["paths가 객체가 아닙니다."]

    operation_ids: list[str] = []
    for path, path_item in paths.items():
        if not isinstance(path_item, dict):
            errors.append(f"{path}: path item이 객체가 아닙니다.")
            continue
        path_parameters = path_item.get("parameters", [])
        if not isinstance(path_parameters, list):
            path_parameters = []
        base_names = _parameter_names(document, path_parameters)

        for method, operation in path_item.items():
            if method.lower() not in HTTP_METHODS or not isinstance(operation, dict):
                continue
            operation_id = operation.get("operationId")
            if not isinstance(operation_id, str) or not operation_id.strip():
                errors.append(f"{method.upper()} {path}: operationId가 없습니다.")
            else:
                operation_ids.append(operation_id)

            operation_parameters = operation.get("parameters", [])
            if not isinstance(operation_parameters, list):
                operation_parameters = []
            names = base_names | _parameter_names(document, operation_parameters)
            for match in PATH_PARAMETER.finditer(path):
                if match.group(1) not in names:
                    errors.append(
                        f"{method.upper()} {path}: 경로 파라미터 {match.group(1)}가 정의되지 않았습니다."
                    )

    duplicates = sorted({value for value in operation_ids if operation_ids.count(value) > 1})
    for duplicate in duplicates:
        errors.append(f"operationId가 중복됩니다: {duplicate}")

    errors.extend(validate_refs(document))
    return errors


def validate_asyncapi(document: object) -> list[str]:
    errors: list[str] = []
    if not isinstance(document, dict):
        return ["AsyncAPI 문서가 객체가 아닙니다."]

    if not str(document.get("asyncapi", "")).startswith("2."):
        errors.append("asyncapi 2.x 버전이 아닙니다.")
    channels = document.get("channels")
    if not isinstance(channels, dict):
        return errors + ["channels가 객체가 아닙니다."]

    for channel_name, channel in channels.items():
        if not isinstance(channel, dict):
            errors.append(f"{channel_name}: 채널이 객체가 아닙니다.")
            continue
        for operation_name in ("publish", "subscribe"):
            operation = channel.get(operation_name)
            if operation is not None and not isinstance(operation, dict):
                errors.append(f"{channel_name}.{operation_name}: 오퍼레이션이 객체가 아닙니다.")

    errors.extend(validate_refs(document))
    return errors
