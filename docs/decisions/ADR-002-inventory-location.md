# ADR-002: Inventory 위치 모델

- 상태: 승인됨
- 날짜: 2026-10-19

## 맥락

Inventory는 Room, Shelf, Box 중 어디에든 존재할 수 있습니다. 위치 계층은 3단계로 고정되어 있으며, 위치 이동과 삭제 규칙을 단순하게 유지해야 합니다.

## 결정

- Inventory는 `room_id`, `shelf_id`, `box_id`를 nullable로 가집니다.
- `CHECK (num_nonnulls(room_id, shelf_id, box_id) = 1)`로 정확히 하나를 강제합니다.
- Shelf는 `room_id`, Box는 `shelf_id`를 필수로 가집니다.
- 위치 FK는 `(id, space_id)` 복합 FK로 같은 Space 참조를 보장합니다.
- 재고 이동은 위치 FK 하나만 갱신합니다.
- 하위 리소스나 Inventory가 있으면 위치를 삭제하지 않고 이동 후 삭제합니다.

## 결과

- 위치 조회와 이동이 단순합니다.
- 선반이나 박스를 이동해도 Inventory 행을 갱신할 필요가 없습니다.
- 방 전체 재고 조회는 직접 위치와 하위 위치를 합치는 쿼리가 필요합니다.
- 위치별 인덱스가 필요합니다.

## 대안

- 단일 `LocationNode` FK: 깊이 가변에는 유리하지만 3단계 고정 요구에는 과합니다.
- Closure table: 계층 조회는 빠르지만 현재 규모에 비해 복잡합니다.
- 위치 문자열 저장: 무결성과 이동 처리가 어렵습니다.

## 링크

- [도메인](../domain.md)
- [데이터 사전](../generated/data-dictionary.md)
