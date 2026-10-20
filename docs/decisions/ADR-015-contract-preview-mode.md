# ADR-015: M0 계약 생성 미리보기 모드

- 상태: 승인됨
- 날짜: 2026-10-20

## 맥락

[ADR-014](ADR-014-generated-docs-draft.md)는 구현 착수 전까지 `docs/generated/`의 OpenAPI, 이벤트, 데이터 사전을 설계 초안으로 수동 갱신하도록 정했습니다. M0에서 springdoc, 스키마 introspection, AsyncAPI export 생성기를 도입하면 생성물을 어디에 기록할지 결정해야 합니다.

M0의 실제 코드에는 `GET /api/v1/info`와 Flyway 확장 이력만 있습니다. 이 상태에서 생성기를 `docs/generated/`에 바로 쓰면 OpenAPI 54개 경로, 이벤트 5개 채널, 데이터 사전의 상세 설계가 최소 계약으로 덮어써집니다. 반대로 생성기를 전혀 실행하지 않으면 계약 생성 절차와 검사가 검증되지 않습니다.

## 결정

- M0에서 계약 생성 결과는 `build/contracts/`에만 기록합니다.
- `docs/generated/openapi.yaml`, `docs/generated/events.yaml`, `docs/generated/data-dictionary.md`는 M0 동안 설계 초안으로 보존합니다.
- `scripts/docs_generate.sh`는 `openapi`, `schema`, `events`, `all`, `check` 명령을 제공하며 기본 동작은 미리보기 생성입니다.
- `scripts/docs_check.py`는 설계 초안과 미리보기를 모두 검사합니다. 미리보기가 아직 없으면 경고만 출력하고 통과합니다.
- M1에서 실제 컨트롤러와 도메인 테이블이 생기면 생성 결과를 `docs/generated/`에 기록하는 모드로 전환하고, ADR-014의 generated 문서 규칙을 다시 적용합니다.
- `build/contracts/`는 빌드 산출물이므로 커밋하지 않습니다.

## 결과

- M0에서도 생성기 명령, operationId·경로 파라미터 검사, AsyncAPI 참조 검사, 데이터 사전 introspection이 실제로 검증됩니다.
- 상세 API·이벤트·스키마 설계 초안이 보존됩니다.
- M1에서 기록 모드로 전환하는 후속 작업이 필요합니다.
- CI의 contract job은 미리보기 생성과 `docs_check.py`를 수행합니다.

## 대안

- M0부터 `docs/generated/`를 덮어쓴다: 상세 설계가 최소 계약으로 손실됩니다.
- 설계 초안을 `docs/design/`으로 이동하고 generated를 새로 시작한다: 단일 원본이 둘로 나뉘고 M1 전까지 계약 링크가 깨집니다.
- M0에서는 생성기를 실행하지 않는다: 생성기와 검사 스크립트의 실제 동작을 검증할 수 없습니다.

## 링크

- [ADR-014](ADR-014-generated-docs-draft.md)
- [구현 로드맵](../implementation-roadmap.md)
- [M0 계약·CI·검증 계획](../implementation/PLAN-001-foundation-delivery.md)
- [문서 검사 스크립트](../../scripts/docs_check.py)
