# 검색과 추천

> 상태: 확정
> 역할: 키워드 검색, 필터, 정렬, 최근 검색, 자주 본 물건, 결과 그룹핑의 단일 원본
> 관련 문서: [도메인](domain.md), [공유](sharing.md), [미디어·AI](media-ai.md), [API 계약](contracts.md), [ADR-004](decisions/ADR-004-mybatis.md), [ADR-012](decisions/ADR-012-embedding-versioning.md)

## 1. 검색 범위

검색은 다음 범위를 포함합니다.

- 내 Space
- 공유받은 Room과 Shelf의 하위 Inventory

공유 결과에는 출처를 표시합니다.

- `내 공간`
- `공유받음: {소유자 이름}`
- 필요하면 결과 필터로 `내 것만`, `공유받은 것만`을 선택할 수 있습니다.

기본 검색에서 제외하는 항목:

- `DISPOSED` Inventory
- soft delete된 리소스
- 접근 권한이 없는 Space의 리소스

## 2. 검색 대상

| 대상 | 검색 방식 |
|---|---|
| 이름 | effective name |
| 설명 | effective description |
| 위치 | Room, Shelf, Box 이름과 경로 |
| 상태 | Inventory status |
| 카테고리 | effective category 이름과 필터 |
| 공유 출처 | 소유자 표시와 필터 |
| 사진 유무 | 대표 이미지 존재 여부 |

- 태그는 사용하지 않습니다.
- 위치 검색은 하위 위치를 포함합니다.
- 예: `냉장고` 검색 시 냉장고 Shelf 아래 Box와 Inventory를 포함합니다.
- 이름과 설명은 오버라이드가 있으면 재고 값을 사용합니다.
- 그룹의 `itemName`은 Item 마스터 이름이고, Inventory별 `effectiveName`은 각자의 오버라이드를 적용합니다.
- 그룹의 `effectiveCategory`는 Item 마스터 Category이며, Inventory별 effective category는 각 Inventory 항목에 표시합니다.

## 3. 검색 엔진

MVP는 PostgreSQL을 사용합니다.

- `pg_trgm` 확장을 사용합니다.
- `lower()` 정규화 후 GIN 인덱스를 사용합니다.
- `ILIKE`와 trigram similarity를 조합합니다.
- `simple` FTS를 보조로 사용합니다.
- 한국어 형태소 분석기는 MVP에 도입하지 않습니다.
- 검색 품질이 부족하면 Meilisearch 도입을 3차 이후 검토합니다.

### 3.1 인덱스 후보

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_item_name_trgm
  ON item USING gin (lower(name) gin_trgm_ops);

CREATE INDEX idx_inventory_name_override_trgm
  ON inventory USING gin (lower(name_override) gin_trgm_ops);

CREATE INDEX idx_room_name_trgm
  ON room USING gin (lower(name) gin_trgm_ops);

CREATE INDEX idx_shelf_name_trgm
  ON shelf USING gin (lower(name) gin_trgm_ops);

CREATE INDEX idx_box_name_trgm
  ON box USING gin (lower(name) gin_trgm_ops);
```

- effective name은 두 테이블에 걸친 COALESCE 표현식이므로 단일 인덱스로 처리되지 않습니다.
- 1차 구현은 `name_override IS NOT NULL`과 `name_override IS NULL` 두 분기를 UNION ALL로 나누고 각자의 trigram 인덱스를 사용합니다.
- 초기 목표는 검색 p95 300ms 이하입니다.
- 목표를 넘으면 effective field와 위치 경로를 담은 `inventory_search` 읽기 모델을 추가합니다.
- 읽기 모델은 outbox 이벤트로 갱신하고, 검색 인덱스와 원본의 차이를 재생성 job으로 복구합니다.
- inventory 수가 작은 초기에는 조인 쿼리로 충분합니다.

## 4. 랭킹

기본 정렬은 관련도 점수입니다. 단, `q`가 없으면 관련도 점수를 계산하지 않고 최근 수정순으로 정렬합니다.

```text
score =
  exact_name_match * 100
  + prefix_name_match * 50
  + name_contains * 30
  + trigram_similarity * 20
  + description_contains * 10
  + recency_bonus
  + view_frequency_bonus
```

- 정확히 일치하는 이름을 가장 높게 평가합니다.
- 이름 접두사 일치, 이름 포함, 설명 포함 순으로 가중치를 둡니다.
- `recency_bonus = max(0, 10 - days_since_updated)`로 계산합니다.
- `view_frequency_bonus = min(10, view_count_90d)`로 계산합니다.
- `trigram_similarity`는 0에서 1 사이 값입니다.
- `DISPOSED`는 기본 결과에서 제외합니다.
- 동점이면 `updated_at DESC`, `item_id` 순으로 정렬합니다.

사용 가능한 정렬:

- 관련도
- 최근 수정순
- 이름순
- 최근 조회순
- 상태순

### 4.1 페이지네이션

- `UPDATED_DESC`는 `(updated_at, item_id)` keyset 커서를 사용합니다. `q`가 없으면 이 정렬이 기본입니다.
- `RELEVANCE`는 첫 페이지만 제공합니다. `nextCursor`는 null이며, 커서를 보내면 `400 CURSOR_NOT_SUPPORTED_FOR_SORT`입니다.
- `NAME_ASC`, `VIEWED_DESC`, `STATUS_ASC`는 1차에서 기존 offset 커서를 유지합니다.
- keyset 페이지는 20개 Item 그룹을 먼저 고른 뒤, 각 그룹의 Inventory를 최대 5개까지 재조회합니다.
- 기존 offset 커서로 `UPDATED_DESC`를 호출하면 `400 INVALID_CURSOR`입니다. 배포 시 검색 커서가 한 번 무효화됩니다.

## 5. 결과 단위

검색 결과는 Inventory를 기준으로 조회하되 Item으로 그룹핑합니다.

```json
{
  "itemId": "...",
  "itemName": "무선 이어폰",
  "matchedInventoryCount": 2,
  "inventoryCount": 2,
  "hasMoreInventories": false,
  "primaryImageId": "...",
  "effectiveCategory": "전자기기",
  "inventories": [
    {
      "inventoryId": "...",
      "effectiveName": "무선 이어폰",
      "status": "STORED",
      "locationPath": "거실 > 서랍장 > 2번 박스",
      "shared": false
    },
    {
      "inventoryId": "...",
      "effectiveName": "무선 이어폰(여행용)",
      "status": "IN_USE",
      "locationPath": "침실 > 책상",
      "shared": true,
      "ownerName": "홍길동"
    }
  ]
}
```

- 한 그룹은 최대 5개의 Inventory를 반환합니다.
- `inventoryCount`는 그룹의 전체 일치 Inventory 수입니다.
- `hasMoreInventories`가 true이면 `GET /items/{itemId}/inventories`로 전체 목록을 조회합니다.
- `primaryImageId`는 그룹 대표 이미지 ID이며 presigned URL이 아닙니다.
- 마스터 이름과 재고별 차이를 함께 보여줍니다.
- 위치와 상태를 칩으로 표시합니다.
- 공유 결과에는 소유자 배지를 표시합니다.
- 결과가 많으면 마스터 카드부터 보여주고 재고를 펼칩니다.
- 이미지 검색 결과도 같은 그룹핑 규칙을 사용합니다.

## 6. 필터

- 상태: 기본 `IN_USE`, `STORED`, 필요 시 `DISPOSED`
- 위치: Room, Shelf, Box
- 카테고리: effective category
- 공유 범위: 내 것, 공유받은 것
- 소유자: 공유 리소스의 소유자
- 사진 유무
- 정렬: 관련도, 최근 수정순, 이름순, 최근 조회순, 상태순
- 가격 범위는 3차 이후

필터는 검색어와 독립적으로 적용할 수 있습니다.

- `status`를 생략하면 `IN_USE`, `STORED`를 적용합니다.
- `DISPOSED`는 명시적으로 요청할 때만 포함합니다.
- `roomId`는 하위 Shelf와 Box를 포함합니다.
- `shelfId`는 하위 Box를 포함합니다.
- `boxId`는 해당 Box만 대상으로 합니다.
- `categoryId`는 effective category를 기준으로 비교합니다.

## 7. 최근 검색

- 사용자별로 서버에 저장합니다.
- 최대 10개를 유지합니다.
- 정규화된 검색어를 기준으로 중복을 제거합니다.
- 마지막 검색 시각을 갱신합니다.
- 30일이 지난 검색어는 정리합니다.
- `q`가 비어 있지 않고 `cursor`가 없는 첫 페이지 요청만 기록합니다.
- 필터만 변경한 요청과 페이지 이동은 기록하지 않습니다.
- 클릭하면 같은 검색어로 다시 검색합니다.
- 민감한 검색어는 저장하지 않도록 필터링할 수 있습니다.

## 8. 자주 본 물건

- Inventory 상세 조회 또는 검색 결과 클릭 시 조회 이벤트를 기록합니다.
- 사용자와 Item 기준으로 `view_count`와 `last_viewed_at`을 upsert합니다.
- 같은 사용자에서 30분 이내 중복 조회는 1회로 계산합니다.
- 최근 90일 기준 상위 10개를 제공합니다.
- 클릭하면 해당 Item의 Inventory 목록으로 이동합니다.
- 전역 인기 순위는 MVP에서 제공하지 않습니다.

## 9. 추천 토큰

검색 화면에서 다음 토큰을 제공합니다.

| 토큰 | 데이터 | 클릭 동작 |
|---|---|---|
| 최근 검색 | `recent_search` | 검색 재실행 |
| 자주 본 물건 | `user_item_view` | Item 상세로 이동 |
| 최근 추가한 재고 | Inventory 생성 시각 | Inventory 상세로 이동 |
| 공유받은 최근 항목 | Share 수락 시각 | 공유 리소스로 이동 |

- `GET /search/suggestions`가 네 종류의 토큰을 한 번에 반환합니다.
- 토큰은 종류별 최대 10개씩 노출합니다.
- 검색어가 비어 있을 때만 노출합니다.
- 검색어 입력을 시작하면 토큰을 숨깁니다.
- 접근 권한이 철회된 항목은 토큰에서도 제외합니다.

## 10. 이미지 검색

- 사용자가 이미지를 업로드하면 서버가 동기로 임베딩을 생성합니다.
- 검색용 이미지는 API에 직접 multipart로 업로드합니다.
- 서버는 검색용 이미지를 임시로 처리하고 원본을 저장하지 않습니다.
- 임베딩 호출 타임아웃은 5초입니다.
- 타임아웃 또는 provider 오류 시 `503 IMAGE_SEARCH_UNAVAILABLE`을 반환합니다.
- 접근 가능한 Inventory의 effective embedding과 비교합니다.
- 현재 설정된 임베딩 모델·버전과 일치하는 벡터만 사용합니다.
- 상위 결과를 마스터로 그룹핑합니다.
- 이미지 검색의 모델, 인덱스, 임계값은 [미디어·AI](media-ai.md)를 따릅니다.
- 키워드 필터와 이미지 검색을 함께 적용할 수 있습니다.
- 사용자별 호출 한도와 레이트리밋을 적용합니다.

## 11. 페이지네이션

- 커서 기반 페이지네이션을 사용합니다.
- 페이지 단위는 Item 그룹입니다.
- 기본 페이지 크기는 20개 그룹입니다.
- 최대 페이지 크기는 100개 그룹입니다.
- 커서는 정렬 키와 `itemId`를 포함합니다.
- 그룹 내 Inventory는 최대 5개까지 반환합니다.
- 결과가 없으면 빈 상태 UI를 제공합니다.
- 총 개수는 필요할 때만 별도 count 쿼리로 제공합니다.
- 잘못된 커서는 `400 INVALID_CURSOR`를 반환합니다.

## 12. API

검색 API의 경로와 스키마는 [OpenAPI 계약](generated/openapi.yaml)을 따릅니다.

- `GET /search`
- `GET /search/recent`
- `GET /search/frequent`
- `GET /search/suggestions`
- `POST /search/image`
- `DELETE /search/recent`
- `GET /items/{itemId}/inventories`

## 13. 성능과 인덱스

- `space_id`, `status`, `category_id`를 인덱스 선두에 둡니다.
- 위치 조인은 Room, Shelf, Box의 FK 인덱스를 사용합니다.
- 공유 조건은 `EXISTS`와 Grant 인덱스를 사용합니다.
- 검색 결과가 느리면 effective field를 materialized view 또는 `inventory_search` 읽기 모델로 분리합니다.
- 이미지 검색은 pgvector HNSW 인덱스를 사용합니다.
- ACL 필터와 벡터 검색을 함께 사용할 때 `limit * 5`, 최대 200개 후보를 가져온 뒤 필터링합니다.
- 후보가 부족하면 후보 수를 늘리거나 pgvector iterative scan을 사용합니다.
- 이미지 검색 결과도 Item 그룹 단위로 페이지네이션합니다.


## 12. 현재 구현 메모

- `GET /search`는 Item 그룹 결과를 반환하고, Inventory는 그룹당 최대 5개를 포함합니다.
- `scope`, 상태·위치·카테고리·소유자·사진 필터와 정렬 5종을 지원합니다.
- `GET /search/recent`, `DELETE /search/recent`, `GET /search/frequent`, `GET /search/suggestions`를 제공합니다.
- `POST /items/{itemId}/view`는 30분 디바운스로 조회 횟수를 기록합니다.
- 현재 cursor는 그룹 offset을 opaque Base64로 감싼 **MVP 구현**입니다. 정렬 키 기반 keyset cursor와 대규모 후보의 정확한 `inventoryCount`는 M6 후속 보완입니다.
- 검색 후보는 최대 500개 Inventory로 제한합니다. 상한에 밀려 빠진 그룹은 해당 Item만 최대 5개 Inventory로 보충 조회하므로 그룹이 페이지에서 사라지지 않습니다. `inventoryCount`는 그룹 쿼리 값을 유지합니다.
- p95 300ms gate는 아직 측정하지 않았습니다. M6.0 성능 시드와 `EXPLAIN` 결과로 검증해야 합니다.
