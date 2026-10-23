# PLAN-007: M6 검색과 추천 상세 구현 계획

> 상태: M6 보완 진행. 그룹 keyset cursor·정확한 집계·추천 ACL·검색어 없는 필터·계약 제약 구현, 성능 gate 검증 중
> 역할: 구현 로드맵 M6의 키워드 검색, 그룹 페이지네이션, 추천, 성능 gate, 검색 UI 계획
> 상위 문서: [구현 로드맵](../implementation-roadmap.md) · 선행 계획: [PLAN-001](PLAN-001-foundation.md), [PLAN-005 공유](PLAN-005-sharing.md)
> 후속 계획: [PLAN-008 M7 AI·이미지 검색](PLAN-008-ai-image-search.md)
> 관련 문서: [검색](../search.md), [도메인](../domain.md), [공유](../sharing.md), [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md), [계약](../contracts.md), [운영](../operations.md), [OpenAPI](../generated/openapi.yaml), [데이터 사전](../generated/data-dictionary.md), [ADR-003](../decisions/ADR-003-permission-model.md), [ADR-004](../decisions/ADR-004-mybatis.md), [ADR-012](../decisions/ADR-012-embedding-versioning.md), [ADR-016](../decisions/ADR-016-neo-brutal-design-system.md), [ADR-019](../decisions/ADR-019-search-acl-pagination.md)

## 1. 목적과 범위

M6은 Inventory를 기준으로 검색하되 Item 그룹으로 안정적으로 페이지네이션하고, 공유 ACL과 폐기 제외를 쿼리 안에서 보장하며, SCR-003 검색 화면을 실제 API에 연결합니다. 검색 규칙의 단일 원본은 [검색](../search.md)이며 이 문서는 구현 순서와 계약만 고정합니다.

포함: `pg_trgm` 키워드 검색, effective name·description·category와 위치 경로 검색, 상태·위치·카테고리·공유 범위·소유자·사진 유무 필터, 정렬 5종, Item 그룹 커서, 그룹당 Inventory 5개와 `hasMoreInventories`·`inventoryCount`, 최근 검색, 자주 본 물건, 제안, 조회 디바운스, ACL overfetch, 성능 시드와 p95 gate, 검색·추천 UI.

제외: 이미지 검색과 임베딩(M7), 한국어 형태소 분석, Meilisearch, count API, 저장된 검색, 전역 인기 순위, 가격 범위, 3차 알림·내보내기.

## 2. 선행 조건

| 마일스톤 | M6에 필요한 결과 | 확인 |
|---|---|---|
| M0 (완료) | Flyway, `pg_trgm`, MyBatis, problem+json, 프론트 API 클라이언트 | `./gradlew :backend:test`, `pnpm test` |
| M1 | 세션 인증, `user_id`·`space_id` 컨텍스트, 공통 오류 처리 | `GET /auth/me` |
| M2 | Room·Shelf·Box, `AccessService` 기본 권한 | 계층·권한 테스트 |
| M3 | `item`, `inventory`, `category`, effective field, `DISPOSED`, `version` | effective field·상태 테스트 |
| M4 | `share`, Grant 상속·철회, 공유 출처 판정 | 상속·철회·교차 Space 테스트 |
| M5 | `image` `READY`, 대표 이미지 승격, `primaryImageId` | 대표 이미지·presign 테스트 |

- M4와 M5가 모두 끝난 뒤 시작하고, 미충족이면 ACL·사진 필터를 임시 구현하지 않고 착수를 보류합니다.
- M3의 `/items`, `/inventories` 목록 필터(`q`, `sort`, `hasImage`)는 M6에서 검색 조건으로 확장합니다. M6은 목록 API를 복제하지 않고 공통 필터 DTO를 재사용합니다. `hasImage`와 `VIEWED_DESC`는 M5·M6에서 처음 활성화합니다.
- M4는 ACL 판정(`AccessService`·`ShareGrantLookup`)과 후보 overfetch 상수를 소유합니다. 현재 `ShareAclMapper.xml`은 없으므로 M6.0에서는 `AccessService`가 계산한 접근 가능한 Room/Shelf ID를 후보 CTE에 주입하고, p95 실패 시 SQL EXISTS 조각으로 최적화합니다([ADR-019](../decisions/ADR-019-search-acl-pagination.md)). `search` 모듈은 `backend/src/main/kotlin/com/agolock/search/`에 두고 `inventory`, `sharing`을 읽기 전용으로 참조합니다.

### 2.1 M6.0 de-risk spike (2~3일)

M6.0은 기능 구현 전에 계약·ACL·성능을 검증하는 gate다. 이 단계에서는 검색 UI를 만들지 않는다.

- 계약: `GET /search`와 `POST /items/{itemId}/view`의 요청·응답·오류·커서 예시를 OpenAPI에 고정하고 `pnpm generate:api` 결과를 만든다.
- 데이터: Item 10,000·Inventory 50,000 시드를 만들고 `ANALYZE item`, `ANALYZE inventory` 후 기준 쿼리를 측정한다.
- ACL: `AccessService`가 접근 가능한 Room/Shelf ID를 계산하는 경로를 먼저 구현하고, 검색 결과와 `AccessService.effectivePermission`을 비교하는 parity test 골격을 만든다. SQL EXISTS 최적화는 p95 gate 실패 후에만 한다.
- 쿼리: `effective_inventory → candidate → grouped → page → group_inventories` CTE를 실제 데이터로 실행하고 `EXPLAIN (ANALYZE, BUFFERS)`를 남긴다.
- 결정: 마이그레이션 번호를 `V17`·`V18`로 확정하고, `/` 인덱스의 `SearchPage` 교체와 조회 기록 API 포함을 계약에 반영한다.
- 종료 조건: 한 개의 실제 그룹 페이지 쿼리가 동작하고, ACL parity test 초안이 있으며, p95가 300ms 이하이거나 미달 시 개선안(인덱스·쿼리·읽기 모델 여부)이 결정되어 있어야 한다.
- Go/no-go: 종료 조건을 못 넘으면 M6.1 UI를 시작하지 않는다. 읽기 모델은 p95 gate를 두 번 연속 실패할 때만 [ADR-019](../decisions/ADR-019-search-acl-pagination.md)에 따라 별도 ADR로 검토한다.

## 3. 데이터 모델과 마이그레이션

### 3.1 원칙

- 테이블·컬럼·제약의 단일 원본은 [데이터 사전](../generated/data-dictionary.md)이고, 인덱스 원칙은 [운영](../operations.md) 3.1번 항목을 따릅니다.
- trigram GIN은 `space_id`를 선두에 둘 수 없으므로 부분 인덱스와 ACL overfetch로 보완합니다.
- `inventory_search` 읽기 모델은 4.9번 p95 gate를 두 번 연속 실패할 때만 추가합니다.
- 마이그레이션은 `V17__search_indexes.sql`, `V18__search_recommendations.sql` 및 `V24__search_description_indexes.sql`로 고정합니다. M5 완료 시 번호를 재확인하되 적용된 마이그레이션은 수정하지 않습니다.

### 3.2 검색 인덱스

| 인덱스 | 정의 요약 | 상태·용도 |
|---|---|---|
| `idx_item_name_trgm` | `item (lower(name) gin_trgm_ops)` | 기존 초안. 생성 확인 필요 |
| `idx_inventory_name_override_trgm` | `inventory (lower(name_override) gin_trgm_ops)` where `name_override IS NOT NULL AND deleted_at IS NULL` | 신규. 오버라이드 이름 |
| `idx_room_name_trgm`, `idx_shelf_name_trgm`, `idx_box_name_trgm` | 각 테이블 `lower(name) gin_trgm_ops` where `deleted_at IS NULL` | 신규. 위치 이름 |
| `idx_item_description_trgm` | `item (lower(description) gin_trgm_ops)` where `description IS NOT NULL` | 선택. 설명 검색이 느릴 때 |
| `idx_recent_search_cleanup` | `recent_search (last_searched_at)` | 신규. 30일 정리 job |
| `idx_share_grantee_active` | `share (grantee_user_id, permission)` where `revoked_at IS NULL` | M4 계획과 동일. ACL 조건 |
| `idx_inventory_status_updated` | `inventory (space_id, status, updated_at DESC)` | 기존 인덱스로 부족할 때 |

- `name_override`는 `IS NOT NULL`과 `IS NULL` 두 갈래 `UNION ALL`로 나눠 각자의 trigram 인덱스를 사용합니다([검색](../search.md) 3.1번 항목).
- 설명·위치 이름은 이름 인덱스로 처리되지 않으므로 `ILIKE`와 `similarity`를 조합하고, Seq Scan이 나오면 후보 축소 순서를 조정합니다. GIN 쓰기 비용은 `inventory`, `item` 갱신 핫패스에서 측정합니다.

### 3.3 추천 테이블

| 객체 | M6 사용 | 변경 |
|---|---|---|
| `recent_search` | 최근 검색 10개, 정규화 중복 제거, 30일 정리 | 스키마 변경 없음. 정리 인덱스만 |
| `user_item_view` | 자주 본 물건 상위 10개, 30분 디바운스 | 스키마 변경 없음. upsert 조건으로 처리 |
| `share`, `image` | 공유 범위·출처, `hasImage`·`primaryImageId` | 기존 인덱스와 `READY`·`is_primary` 조건 |

- `recent_search.normalized_query`는 NFKC, 앞뒤 공백 제거, 연속 공백 축소, 소문자 변환 결과이고 원본 `query`는 표시용으로 보존합니다.
- `user_item_view`는 `last_viewed_at <= now() - interval '30 minutes'`일 때만 `view_count`와 시각을 갱신하고, 그 외에는 아무 행도 갱신하지 않습니다.

### 3.4 ACL 조회 구조

M6.0은 M4의 ACL 의미를 `AccessService`에서 유지하고, 검색 SQL에는 접근 가능한 Room/Shelf ID와 소유 Space를 주입합니다. SQL EXISTS 최적화는 M6.0 p95 gate를 실패한 경우에만 적용합니다([ADR-019](../decisions/ADR-019-search-acl-pagination.md)).

```text
resolved_inventory:
  direct room  -> room_id = inventory.room_id, shelf_id = null
  direct shelf -> room_id = shelf.room_id, shelf_id = inventory.shelf_id
  box          -> room_id = shelf.room_id, shelf_id = box.shelf_id

accessible(inv) =
  (inv.space_id = actor.spaceId)
  OR resolved_inventory.room_id = ANY(:accessibleRoomIds)
  OR resolved_inventory.shelf_id = ANY(:accessibleShelfIds)
```

- `resolved_inventory`는 4.2번 `effective_inventory` CTE에 포함하고, 후보 CTE가 접근 가능한 Space와 Room/Shelf ID를 적용합니다. 결과를 가져온 뒤 애플리케이션에서 필터링하지 않습니다.
- `AccessService`에 `accessibleRoomIds`·`accessibleShelfIds`를 추가하고, 검색 결과와 `effectivePermission`을 비교하는 `SearchAclParityTest`를 둡니다. 이 테스트 없이 SQL EXISTS 최적화로 넘어가지 않습니다.
- `scope=OWNED/SHARED`도 같은 소유자·Space 조건을 재사용하고, `ownerId`는 접근 가능한 Space 소유자만 허용합니다.
- Grant 철회는 다음 요청부터 즉시 반영해야 하므로 판정 결과를 캐시하지 않습니다.

### 3.5 FTS와 마이그레이션

`simple` FTS는 trigram 품질과 p95를 먼저 측정한 뒤 한국어 2~3자 재현율이 부족할 때만 보조 인덱스로 추가합니다. 확인 필요. `inventory_search`는 effective field·위치 경로·대표 이미지 ID·상태·`updated_at`·소유자 ID를 담는 후보이며 도입 시 outbox 갱신과 재생성 job을 둡니다.

| 파일 | 내용 |
|---|---|
| `backend/src/main/resources/db/migration/V17__search_indexes.sql` | trigram GIN, `recent_search` 정리, ACL 보조 인덱스 |
| `backend/src/main/resources/db/migration/V18__search_recommendations.sql` | 선택 인덱스가 필요할 때 분리 |

장시간 lock이 필요한 인덱스는 `CREATE INDEX CONCURRENTLY` 사용 여부를 배포 절차와 함께 검토합니다. 확인 필요.

## 4. 검색 설계

### 4.1 쿼리 파이프라인

1. `SearchRequestValidator`가 `q`, `limit`, `sort`, 위치 필터 조합, UUID를 검증합니다.
2. `AccessService`가 접근 가능한 Room·Shelf ID와 소유 Space를 계산합니다.
3. `SearchQueryMapper.selectGroupPage`가 후보·필터·그룹·페이지를 한 SQL로 처리합니다.
4. `SearchAssembler`가 평면 row를 `SearchItemGroup`으로 묶고 공유 출처를 채웁니다.
5. `RecentSearchService`가 4.10번 조건을 만족할 때만 검색어를 기록합니다.

### 4.2 Effective field

`effective_inventory` CTE는 두 갈래 `UNION ALL`입니다.

| 갈래 | 조건 | effective name | effective description | effective category |
|---|---|---|---|---|
| override | `name_override IS NOT NULL` | `name_override` | `COALESCE(description_override, item.description)` | `COALESCE(category_id_override, item.category_id)` |
| master | `name_override IS NULL` | `item.name` | `item.description` | `item.category_id` |

- 두 갈래 모두 `item`, `category`, 위치 조인과 `deleted_at IS NULL`을 포함하고, 고정 깊이 3단계를 이용해 Room·Shelf·Box를 LEFT JOIN해 `location_*_id`와 표시용 이름을 만듭니다. 경로는 `Room > Shelf > Box`이고 직접 위치한 노드까지만 표시합니다.
- 그룹 `itemName`은 Item 마스터 이름, `effectiveCategory`는 Item 마스터 Category이고 Inventory별 값은 각 행을 사용합니다([검색](../search.md) 2번 항목).

### 4.3 필터

| 파라미터 | 매핑 | 기본·규칙 |
|---|---|---|
| `q` | effective name·description, 위치 이름·경로, category name | 최대 200자. 공백이면 필터 전용 |
| `status` | `inventory.status` | 생략 시 `IN_USE`, `STORED`. `DISPOSED`는 명시할 때만 |
| `roomId` | Room과 하위 Shelf·Box | 하위 포함. 다른 위치 필터와 동시 불가 |
| `shelfId` | Shelf와 하위 Box | 하위 포함. 다른 위치 필터와 동시 불가 |
| `boxId` | Box만 | 다른 위치 필터와 동시 불가 |
| `categoryId` | effective category | override가 있으면 override 기준 |
| `scope` | Space 소유 여부 | `ALL` 기본. `OWNED`는 내 Space, `SHARED`는 공유받은 Space |
| `ownerId` | Space 소유자 | 접근 가능한 소유자만 유효. 그 외는 빈 결과 |
| `hasImage` | 대표 이미지 존재 | Inventory `READY` 우선, 없으면 Item `READY` |

- 위치 필터를 두 개 이상 보내면 `400 VALIDATION_FAILED`입니다. 확인 필요.
- `q`는 NFKC 후 공백 토큰으로 나누고 모두 AND로 적용하며, 각 토큰은 이름·설명·위치·카테고리 중 하나 이상에 일치하면 됩니다. 이름은 `ILIKE '%token%'`와 `similarity(lower(effective_name), lower(token)) >= :threshold`를 함께 사용하고 임계값 기본 0.3은 `search.similarity-threshold`입니다.
- 필터만 바꾼 요청은 최근 검색에 기록하지 않습니다.

### 4.4 정렬

그룹 정렬 키와 동점 규칙의 단일 원본은 [검색 4.1번 항목](../search.md)입니다. SQL과 커서는 같은 비교 규칙을 사용합니다.

### 4.5 커서

Base64URL(패딩 없음) JSON입니다.

```json
{"v":1,"s":"UPDATED_DESC","k":"2026-10-20T12:00:00Z","i":"018f...","f":"9f2c1a7b3d4e5f60"}
```

| 필드 | 의미 | 검증 |
|---|---|---|
| `v` | 버전 | `1`이 아니면 `INVALID_CURSOR` |
| `s` | 정렬 | 요청의 유효 정렬과 다르면 오류 |
| `k` | 정렬 키 | 타입별 파싱 실패 시 오류 |
| `i` | 마지막 Item ID | UUID가 아니면 오류 |
| `f` | 필터 지문 | 현재 요청 지문과 다르면 오류 |
| `u` | 마지막 그룹 수정 시각 | 관련도 동점 비교, ISO 8601 시각 |

- 지문은 정규화 `q`, 유효 정렬, `status`, 위치 ID, `categoryId`, `scope`, `ownerId`, `hasImage`를 고정 순서로 연결한 SHA-256 앞 16자이며 `limit`은 제외합니다.
- 한글 이름 정렬 키의 UTF-8·Base64 크기를 고려해 커서는 2,048자를 넘을 수 없고, 초과·디코딩 실패·버전 불일치는 `400 INVALID_CURSOR`입니다. 내림차순 키는 `<`, 오름차순 키는 `>`로 비교하며, 동점의 Item ID는 항상 오름차순입니다. 관련도는 수정 시각 내림차순도 비교합니다.
- 커서에 사용자 ID, Space ID, 검색어 원문을 담지 않습니다. M6는 지문으로 재사용만 막고 HMAC·TTL은 사용하지 않으며, 오류 응답에 커서 원문을 되돌려주지 않습니다. 확인 필요.

### 4.6 그룹 페이지네이션

- 페이지 단위는 Item 그룹, 기본 20·최대 100입니다. `grouped` CTE에서 `item_id`로 묶어 `inventory_count`, 정렬 키, 관련도 최댓값을 계산합니다.
- `page` CTE는 정렬 키와 `item_id`로 정렬해 `limit + 1`개를 가져오고, 추가 1개는 `nextCursor` 판정에만 쓰며 응답에 넣지 않습니다.
- 각 그룹 Inventory는 `LEFT JOIN LATERAL`로 6개를 가져와 5개만 반환하고, 6번째가 있으면 `hasMoreInventories=true`입니다.
- `inventoryCount`는 현재 q와 필터에 일치한 그룹 전체 수이고 `hasMoreInventories`는 `inventoryCount > 5`입니다. `matchedInventoryCount`는 M6에서 `inventoryCount`와 같은 값이며, 구분이 필요하면 12번 항목을 따릅니다.
- `GET /items/{itemId}/inventories`는 접근 가능한 전체 Inventory를 반환하므로 그룹의 일치 수와 다를 수 있습니다. 이 차이는 12.1번 확인 필요로 남깁니다.
- Inventory 행 순서는 상태 순위, `updated_at DESC`, `id ASC`입니다. 그룹별 추가 조회(N+1)를 금지하고 카테고리·소유자·대표 이미지를 같은 SQL에서 조인합니다.
- overfetch는 그룹 `limit + 1`, 그룹 내 Inventory 6개입니다. ACL은 후보 단계에 있으므로 키워드 검색에서 사후 폐기 overfetch를 하지 않습니다.

### 4.7 공유 출처와 DISPOSED

- `shared`는 `inventory.space_id <> :ownSpaceId`이고 공유 재고는 `ownerName`을 함께 반환합니다. 그룹에 내 재고와 공유 재고가 섞이면 Inventory 행마다 배지를, 그룹에는 공유 재고가 있을 때 그룹 배지를 표시합니다.
- `scope=OWNED`는 `space_id = :ownSpaceId`, `SHARED`는 그 외 접근 가능한 Space입니다. `ownerId`가 접근 불가능하면 403이 아니라 빈 결과를 반환해 존재 여부를 노출하지 않습니다.
- `DISPOSED`는 `status`에 명시한 경우에만 포함하고 기본 검색·그룹 집계에서 제외합니다. 폐기만 있는 Item은 기본 결과에 없고, 명시 포함 시 취소선과 `폐기` 라벨을 함께 표시합니다.
- 최근 검색·자주 본 물건·제안에서도 철회된 항목과 폐기만 있는 항목을 제외합니다. 확인 필요.

### 4.8 SQL 구조와 Mapper

`backend/src/main/resources/mapper/search/SearchQueryMapper.xml`의 CTE는 `effective_inventory`(override·master, 위치 조인, `resolved_inventory`), `candidate`(ACL include·필터·q·`DISPOSED`), `grouped`(Item 집계·정렬 키), `page`(커서·정렬·`limit + 1`), `group_inventories`(그룹별 6개)입니다.

- Mapper 메서드는 `selectGroupPage`, `selectFrequentItems`, `selectRecentSearches`, `selectSuggestions`로 나누고 결과는 `SearchGroupRow`, `SearchInventoryRow` DTO로 매핑하며 `Map` 반환은 사용하지 않습니다.
- ACL은 M6.0에서 `AccessService.accessibleRoomIds`·`accessibleShelfIds`를 후보 CTE에 주입하고, `SearchAclParityTest`로 의미를 고정합니다. p95 실패 시에만 `SearchAclMapper.xml` EXISTS 조각으로 최적화합니다.

### 4.9 성능 전략과 p95

| 항목 | 기준 |
|---|---|
| 측정 조건 | 단일 Space, Item 10,000, Inventory 50,000, 공유 10%, 이미지 70%, 20개 그룹 |
| p95·p99 | 300ms 이하·600ms 이하 ([검색](../search.md) 3.1번 항목) |
| 측정 | 워밍업 5회, 측정 20회, `EXPLAIN (ANALYZE, BUFFERS)` 1회 |
| 통과 | p95 300ms 이하. 선택적 키워드 검색의 실제 SQL에서 trigram GIN 사용 확인. 전체 집계 검색은 Seq Scan을 허용 |

- `SearchPerformanceTest`는 `search-perf` 태그로 분리해 기본 테스트에서 제외하고 야간·성능 라벨 PR에서 실행합니다. 시드 후 `ANALYZE inventory`, `ANALYZE item`을 실행하며 통계가 없으면 측정이 무의미합니다.
- p95 초과 시 순서대로 적용합니다: 불필요한 OR 제거, 설명 검색 분리, `inventory_count` 사전 집계, `inventory_search` 읽기 모델. 읽기 모델은 두 번 연속 gate 실패 시에만 새 ADR로 도입합니다.
- 느린 쿼리는 `log_min_duration_statement=300ms`로 수집하고 `agolock.search.duration` 타이머로 별도 기록합니다. `limit` 최대 100도 p95 측정에 포함합니다.

### 4.10 최근 검색, 자주 본 물건, 제안

| 기능 | 규칙 |
|---|---|
| 최근 검색 | `q`가 있고 `cursor`가 없는 첫 페이지가 성공한 경우에만 기록. 최대 10개, 정규화 중복 제거, 마지막 검색 시각 갱신 |
| 최근 검색 정리 | 30일 지난 행을 일 1회 삭제. `DELETE /search/recent`는 사용자 전체 삭제 |
| 자주 본 물건 기록 | `POST /items/{itemId}/view` 등에서 `user_item_view` upsert. 30분 이내 중복은 1회 |
| 자주 본 물건 조회 | 최근 90일, `view_count DESC`, `last_viewed_at DESC`로 최대 10개 |
| 제안 | 검색어가 비었을 때 최근 검색·자주 본 물건·최근 추가 Inventory·공유받은 항목을 각각 최대 10개 |
| 제안 제외 | 권한 철회, 폐기만 있는 Item, 삭제된 리소스 |

- 최근 검색 기록은 검색 응답을 막지 않도록 같은 트랜잭션 밖에서 처리하거나 실패를 무시하고, 민감 검색어 차단 목록은 `search.recent.blocked-terms`로 두며 기본값은 비어 있습니다. 확인 필요.
- 조회 기록 API는 M6.0 계약에 포함하고 5.4번의 204/404·30분 디바운스를 따른다.

## 5. API 계약

### 5.1 엔드포인트

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `/search` | 키워드·필터 검색, Item 그룹 커서 | 필요 |
| GET | `/search/recent` | 최근 검색어 최대 10개 | 필요 |
| DELETE | `/search/recent` | 최근 검색어 전체 삭제 | 필요 |
| GET | `/search/frequent` | 자주 본 물건 최대 10개 | 필요 |
| GET | `/search/suggestions` | 제안 토큰 4종 | 필요 |
| POST | `/items/{itemId}/view` | 조회 기록, 30분 디바운스 | 필요 |
| POST | `/search/image` | 이미지 검색(M7) | 필요 |

응답은 `{ "items": [], "nextCursor": null }`, `DELETE`는 `204 No Content`이고 검색 범위는 서버가 결정합니다.

### 5.2 `GET /search` 파라미터

| 이름 | 형식 | 기본 | 제한 |
|---|---|---|---|
| `q` | string | 없음 | 최대 200자 |
| `status` | enum 배열, `explode=false` | `IN_USE,STORED` | `IN_USE`, `STORED`, `DISPOSED` |
| `roomId` | uuid | 없음 | `shelfId`, `boxId`와 동시 불가 |
| `shelfId` | uuid | 없음 | `roomId`, `boxId`와 동시 불가 |
| `boxId` | uuid | 없음 | `roomId`, `shelfId`와 동시 불가 |
| `categoryId` | uuid | 없음 | effective category |
| `scope` | enum | `ALL` | `ALL`, `OWNED`, `SHARED` |
| `ownerId` | uuid | 없음 | 접근 가능한 소유자만 |
| `hasImage` | boolean | 없음 | 대표 이미지 존재 |
| `sort` | enum | `RELEVANCE` | 5종 |
| `cursor` | string | 없음 | 최대 512자 |
| `limit` | integer | 20 | 1~100 |

### 5.3 응답

```json
{
  "items": [{
    "itemId": "018f...", "itemName": "무선 이어폰",
    "matchedInventoryCount": 2, "inventoryCount": 2, "hasMoreInventories": false,
    "primaryImageId": "018f...", "effectiveCategory": { "id": "018f...", "name": "전자기기" },
    "inventories": [{
      "id": "018f...", "itemId": "018f...", "effectiveName": "무선 이어폰(여행용)",
      "status": "IN_USE", "location": { "type": "BOX", "id": "018f...", "path": "침실 > 책상 > 2번 박스" },
      "shared": true, "ownerName": "홍길동", "primaryImageId": "018f..."
    }]
  }],
  "nextCursor": null
}
```

목록은 presigned URL이 아니라 이미지 ID를 반환하고 클라이언트는 `POST /images/urls`로 일괄 발급합니다. `inventories`는 최대 5개이고 `hasMoreInventories=true`이면 `GET /items/{itemId}/inventories`로 전체를 조회합니다. `effectiveCategory`는 그룹에서 Item 마스터, Inventory 행에서 effective Category입니다.

### 5.4 조회 기록 API

M6.0 계약에 `POST /items/{itemId}/view`를 포함합니다. 접근 가능한 Inventory가 있으면 `204 No Content`, 없으면 `404 RESOURCE_NOT_FOUND`이고 본문은 없습니다.

- `user_item_view`가 Item 기준이므로 검색 결과 클릭과 Item 상세가 같은 키를 씁니다.
- 서버는 30분 디바운스를 적용하고 반복 호출은 성공으로 응답하되 `view_count`를 늘리지 않습니다.
- 이 API가 M6.0 계약에서 빠지면 자주 본 물건과 `VIEWED_DESC` 정렬도 M6 범위에서 제외합니다.

### 5.5 오류와 한도

| 상황 | 상태·코드 |
|---|---|
| `limit`, `sort`, UUID, 위치 필터 조합 오류 | `400 VALIDATION_FAILED` |
| 커서 디코딩·버전·정렬·지문 불일치 | `400 INVALID_CURSOR` |
| 세션 없음·만료 | `401 Unauthorized` |
| 조회 기록 대상에 접근 가능한 Inventory 없음 | `404 RESOURCE_NOT_FOUND` |
| 서버 오류 | `500` |

`q` 200자, `cursor` 512자, `limit` 100, 그룹 Inventory 5개, 제안 종류별 10개, 최근 검색 10개, 자주 본 물건 10개가 한도이며 검색에는 `Idempotency-Key`를 쓰지 않습니다.

## 6. 프론트엔드

### 6.1 화면과 컴포넌트

| 경로 | 역할 |
|---|---|
| `frontend/src/features/search/search-page.tsx` | SCR-003 조립, URL 상태, 쿼리 |
| `frontend/src/features/search/search-field.tsx` | `SearchField`를 감싼 입력과 300ms 디바운스 |
| `frontend/src/features/search/filter-panel.tsx` | 상태·위치·카테고리·공유 범위·소유자·사진 필터 |
| `frontend/src/features/search/result-group-card.tsx` | Item 그룹 카드, 5개 Inventory, 더 보기 |
| `frontend/src/features/search/inventory-row.tsx` | 위치·상태·소유자·오버라이드 배지 |
| `frontend/src/features/search/recent-searches.tsx`, `frequent-items.tsx`, `suggestion-panel.tsx` | 최근 검색, 자주 본 물건, 제안 |
| `frontend/src/features/search/search-states.tsx` | 로딩·빈 상태·오류·재시도 |
| `frontend/src/features/search/api.ts` | TanStack Query 훅과 API 타입 |
| `frontend/src/app/router.tsx` | `/` 인덱스 라우트를 `SearchPage`로 교체하고 기존 `HomePage` 콘텐츠를 검색 빈 상태로 흡수 |

M6.0에서 `/` 인덱스를 `SearchPage`로 교체하는 것을 확정한다. 기존 `HomePage`는 별도 홈으로 유지하지 않고, 최근 검색·자주 본 물건·최근 추가 콘텐츠를 검색 화면의 빈 상태로 옮긴다.

기존 `shared/ui/`의 `Badge`, `Card`, `StatusTag`, `Thumbnail`, `Button`, `SearchField`를 쓰고 새 색·간격을 만들지 않습니다. 데스크톱은 결과 영역과 오른쪽 필터 패널(최소 260px), 모바일은 필터 하단 시트이며 시각 규칙은 [디자인 시스템](../design-system.md)을 따릅니다.

### 6.2 상호작용

| 동작 | 처리 |
|---|---|
| 검색어 입력 | 300ms 디바운스 후 첫 페이지 조회, 입력 중 제안 숨김 |
| 검색 실행 | `q`를 URL에 반영하고 커서 초기화 |
| 필터·정렬 변경 | 즉시 첫 페이지 재조회, `cursor` 초기화, URL 반영 |
| 더 보기 | 카드 펼침 또는 `GET /items/{itemId}/inventories` 조회 |
| 최근 검색·자주 본 물건 클릭 | 같은 검색어로 재검색, Item 상세로 이동 |
| 결과 클릭 | Inventory 상세로 이동하고 `POST /items/{itemId}/view` 호출 |
| 이미지 검색 | M7 전까지 비활성 칩과 “2차 제공” 안내 |

- `q`, `status`, 위치, `categoryId`, `scope`, `ownerId`, `hasImage`, `sort`는 URL에 두고 공유·뒤로 가기를 지원하며, `cursor`는 컴포넌트 상태로 둡니다.
- 조회 기록은 클라이언트에서도 같은 Item 30분 디바운스를 적용하고 서버 디바운스와 중복 호출을 모두 방어합니다. 필터 초기화, 적용 중 필터 칩, `DISPOSED` 경고 문구를 제공합니다.

### 6.3 상태와 접근성

| 상태 | UI |
|---|---|
| 로딩 | 카드 스켈레톤 3개, 필터 패널 유지 |
| 빈 결과 | 검색어·필터 요약, 초기화, 추천 토큰 |
| 오류 | 오류 코드와 재시도, `INVALID_CURSOR`면 첫 페이지 재조회 |
| 세션 만료·네트워크 오류 | 로그인 이동, 오프라인 안내와 온라인 복구 시 재조회 |
| 이미지 검색 불가 | M7 안내, 키워드 검색 전환 |

- 입력에 `<label>`과 `aria-describedby`, 결과 수 변화에 `aria-live="polite"`를 제공하고, 펼침은 `aria-expanded`, `aria-controls`와 키보드 조작을 지원합니다.
- 칩·버튼 터치 영역 44px 이상, 상태는 색과 텍스트를 함께 사용합니다.
- 모바일 390px·데스크톱 1280px에서 확인하고, 필터 시트는 포커스 트랩과 닫기 후 복귀를 구현합니다. 긴 위치는 말줄임과 툴팁을 제공합니다.

## 7. 작업 분해

| 순서 | 작업 | 주요 파일 | 검증 |
|---|---|---|---|
| 0 | M6.0 계약·시드·ACL·p95 스파이크 | OpenAPI 예시, `SearchQueryMapper.xml` 초안, `SearchSeedFixture.kt`, `SearchAclParityTest` 골격 | `EXPLAIN`, p95 기준, 계약 diff, ACL parity |
| 1 | 인덱스·마이그레이션 | `V16__search_indexes.sql`, `V17__search_recommendation_indexes.sql` | Flyway, `EXPLAIN` GIN |
| 2 | 커서·도메인 | `search/domain/SearchQuery.kt`, `SearchSort.kt`, `SearchCursorCodec.kt`, `SearchFilter.kt` | `SearchCursorTest`, `SearchSortTest` |
| 3 | MyBatis SQL | `mapper/search/SearchQueryMapper.xml`, `SearchMapper.kt`, `SearchGroupRow.kt` | `SearchFilterTest`, `SearchGroupPaginationTest`, `SearchAclTest` |
| 4 | 서비스·추천 | `search/application/SearchService.kt`, `RecentSearchService.kt`, `UserItemViewService.kt`, `SuggestionService.kt` | `RecentSearchTest`, `UserItemViewTest`, `SearchSuggestionsTest` |
| 5 | 컨트롤러·계약 | `search/api/SearchController.kt`, `SearchDtos.kt`, `POST /items/{itemId}/view`, OpenAPI 초안, `pnpm generate:api` | 계약 검사, 타입 diff 없음 |
| 6 | 검색 UI | `frontend/src/features/search/**`, `frontend/src/app/router.tsx` | `search-page.test.tsx`, `result-group-card.test.tsx`, `filter-panel.test.tsx` |
| 7 | 성능 시드·gate | `scripts/seed_search.py`, `SearchSeedFixture.kt`, `SearchPerformanceTest.kt`, CI job | p95 300ms 이하, EXPLAIN 보고서 |
| 8 | 문서·관측 | `docs/search.md` 보강, 데이터 사전, 계약, 대시보드 | `python3 scripts/docs_check.py` |

0~3번을 먼저 끝내고 6번은 실제 응답으로 개발합니다. 각 단계 종료 시 `./gradlew ktlintCheck detekt test`와 `pnpm lint && pnpm test && pnpm build`를 실행합니다.

### 7.1 Walking skeleton과 수직 슬라이스

1. M6.1: q만 있는 `GET /search`, `UPDATED_DESC`, 20개 그룹, 커서, 그룹당 5+1개 Inventory, `/` 검색 라우트를 연결한다.
2. M6.2: 상태·위치·카테고리·공유 범위·소유자·사진 필터, 정렬 5종, `DISPOSED`·공유 출처, ACL parity를 추가한다.
3. M6.3: `POST /items/{itemId}/view`, 최근 검색, 자주 본 물건, 제안, 30일 정리를 연결한다.
4. M6.4: 필터 패널·그룹 카드·상태 UI, 모바일 시트, p95 gate, 관측·문서를 닫는다.

- 첫 end-to-end 경로가 서기 전에는 정렬 5종을 한꺼번에 만들지 않는다.
- 각 슬라이스는 백엔드 테스트와 프론트 테스트를 함께 통과해야 다음으로 넘어간다.
- M6.0 gate를 통과하지 못하면 M6.1 UI를 시작하지 않는다.

## 8. 테스트

### 8.1 백엔드

| 테스트 | 확인 |
|---|---|
| `SearchFilterTest` | 상태 기본값, `DISPOSED` 명시, Room·Shelf 하위, Box 정확, effective category, scope, owner, hasImage, 위치 2개 오류 |
| `SearchSortTest` | 5종, 동점 `item_id`, 빈 q의 `RELEVANCE` 대체, `VIEWED_DESC` NULL 뒤 |
| `SearchCursorTest` | 왕복, 버전·정렬·지문 불일치, 잘못된 Base64, 512자 초과, `400 INVALID_CURSOR` |
| `SearchGroupPaginationTest` | 5개 제한, 6번째 `hasMoreInventories`, `inventoryCount`, `nextCursor` 중복 없음, 마지막 `null` |
| `SearchDisposedTest` | 기본 제외, 명시 포함, 폐기만 있는 그룹 제외, 최근 검색·제안 제외 |
| `SearchAclTest` | 소유자, Room·Shelf 상속, 철회 즉시 차단, 교차 Space 0건, 이동 후 가시성, `shared`·`ownerName` |
| `SearchAclParityTest` | 검색 결과와 `AccessService.effectivePermission`이 소유자·Room/Shelf Grant·철회·교차 Space에서 일치 |
| `ItemViewApiTest` | 204/404, 30분 디바운스, `view_count`·`last_viewed_at` 갱신, 반복 호출 멱등 |
| `RecentSearchTest` | 첫 페이지·q 있음만 기록, 필터만 변경 미기록, 정규화 중복, 10개 초과 삭제, 30일 정리 |
| `UserItemViewTest` | 30분 디바운스, `view_count` 증가, `last_viewed_at` 갱신, 중복 호출 멱등 |
| `SearchSuggestionsTest` | 4종 각 10개, 철회·폐기·접근 불가 제외 |
| `SearchPerformanceTest` | p95·p99, `EXPLAIN` GIN, Seq Scan 없음 |

### 8.2 프론트엔드

| 테스트 | 확인 |
|---|---|
| `search-page.test.tsx` | 300ms 디바운스, URL 상태, 로딩·빈·오류, 필터 초기화 |
| `result-group-card.test.tsx` | 5개 행, 더 보기, `hasMoreInventories`, 공유 배지, `aria-expanded` |
| `filter-panel.test.tsx` | 체크박스, 위치 선택, 모바일 시트 포커스, 적용·초기화 |
| `recent-searches.test.tsx`, `search-field.test.tsx` | 재검색·전체 삭제·빈 상태, 입력 지연·제안 숨김·키보드 제출 |

Playwright `frontend/e2e/search.spec.ts`는 백엔드·시드가 있는 E2E 환경이 준비된 뒤 추가하고, M6에서는 컴포넌트 테스트를 필수로 합니다. 확인 필요.

## 9. 계약, 문서, CI, 관측, 시드

### 9.1 계약과 문서

- [OpenAPI](../generated/openapi.yaml)에 `POST /items/{itemId}/view`, 커서 최대 길이, `400 INVALID_CURSOR` 예시, `inventoryCount`·`hasMoreInventories` 설명을 반영합니다.
- 검색 ACL·커서·그룹 페이지네이션 결정은 [ADR-019](../decisions/ADR-019-search-acl-pagination.md)를 링크하고, 접근 ID 주입과 SQL EXISTS 최적화 조건을 [검색](../search.md)에 반영합니다.
- [데이터 사전](../generated/data-dictionary.md)에 3.2번 신규 인덱스와 3.3번 정리 인덱스를 반영하고, [검색](../search.md)에 커서 지문, 빈 q 정렬 대체, `matchedInventoryCount`, 조회 기록 API, 그룹 대표 이미지 우선순위를 반영합니다.
- [계약](../contracts.md)은 페이지네이션 규칙과 링크만 두고 검색 세부 규칙을 복제하지 않습니다. generated 문서는 생성기 기록 모드에서 재생성하며, 생성기가 아직 없으면 [ADR-014](../decisions/ADR-014-generated-docs-draft.md)에 따라 초안을 수동 갱신하고 머리말을 유지합니다.

### 9.2 CI

backend job은 `./gradlew ktlintCheck detekt test`, search-perf job은 야간·`search-perf` 라벨 PR에서 `./gradlew :backend:searchPerfTest`와 `EXPLAIN` artifact를 실행합니다. frontend job은 `pnpm generate:api`, `pnpm lint`, `pnpm test`, `pnpm build`, 타입 diff를 확인하고 docs job은 `python3 scripts/docs_check.py`를 실행합니다. 성능 gate는 고정 시드·고정 데이터·워밍업 후 단일 p95로 판정합니다. 확인 필요.

### 9.3 관측

| 지표·로그 | 내용 |
|---|---|
| `agolock.search.duration` | `sort`, `scope`, `hasQ` 태그, p50·p95·p99 |
| `agolock.search.empty` | 결과 0건 비율 |
| `agolock.search.invalid_cursor` | 커서 오류 수 |
| `agolock.search.recent.pruned` | 30일 정리 건수 |
| `agolock.search.view.debounced` | 디바운스로 무시된 조회 수 |
| 구조화 로그 | `request_id`, `user_id`, `space_id`, `sort`, `filterCount`, `groupCount`, `durationMs`. 검색어 원문은 남기지 않음 |

p95가 10분간 300ms를 넘으면 알림을 보내고, `invalid_cursor` 급증은 클라이언트 회귀 신호로 봅니다. 느린 쿼리와 `EXPLAIN`은 배포 전후로 비교합니다.

### 9.4 시드 데이터

`scripts/seed_search.py`는 `--reset --seed 42`로 소유자 1명·공유 사용자 1명, Room 20·Shelf 100·Box 500, Item 10,000·Inventory 50,000(상태 50/45/5, 공유 10%, 이미지 70%), 최근 검색 5,000·조회 20,000을 만들고 `ANALYZE`를 실행합니다. 로컬·테스트 DB만 대상으로 하고 운영 URL은 거부합니다. `SearchSeedFixture`는 Testcontainers에서 같은 분포를 배치 insert로 만들어 perf 태그에서만 사용합니다.

## 10. 완료 체크리스트

- [ ] M6.0에서 `GET /search`·`POST /items/{itemId}/view` 계약, 시드, ACL parity, p95 기준을 통과했습니다.
- [ ] `pg_trgm` GIN 인덱스가 적용되고 `EXPLAIN`에서 사용됩니다.
- [ ] effective name·description, 위치 하위, effective category 검색이 테스트됩니다.
- [ ] 상태·위치·카테고리·공유 범위·소유자·사진 필터와 정렬 5종·동점 처리가 테스트됩니다.
- [ ] 빈 q의 `RELEVANCE` 대체가 테스트됩니다.
- [ ] 그룹 커서 왕복과 `INVALID_CURSOR` 400이 테스트됩니다.
- [ ] 그룹당 Inventory 5개, `inventoryCount`, `hasMoreInventories`가 테스트됩니다.
- [ ] 기본 `DISPOSED` 제외와 명시 포함이 테스트됩니다.
- [ ] 공유 `shared`·`ownerName`과 철회 후 즉시 차단이 테스트됩니다.
- [ ] 최근 검색 기록 조건·10개 제한·30일 정리가 테스트됩니다.
- [ ] 조회 30분 디바운스와 자주 본 물건 상위 10개가 테스트됩니다.
- [ ] 제안 4종 각 10개와 권한 없는 항목 제외가 테스트됩니다.
- [ ] 시드 50,000 Inventory에서 p95 300ms 이하와 EXPLAIN gate를 통과합니다.
- [ ] SCR-003 검색·필터·결과 카드·최근 검색·자주 본 물건·제안 UI가 API와 연결됩니다.
- [ ] `/` 인덱스가 `SearchPage`로 교체되고 기존 `HomePage` 콘텐츠가 검색 빈 상태로 흡수됩니다.
- [ ] 조회 기록 API의 204/404·30분 디바운스가 테스트됩니다.
- [ ] 로딩·빈 상태·오류·세션 만료·오프라인 상태가 구현됩니다.
- [ ] 모바일 390px·데스크톱 1280px, 키보드·스크린리더 기본 요건을 확인합니다.
- [ ] OpenAPI·데이터 사전·프론트 타입이 재생성되고 `pnpm generate:api` diff가 없습니다.
- [ ] `python3 scripts/docs_check.py`가 통과하고 M7 이미지 검색·임베딩 코드가 포함되지 않습니다.

## 11. 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 한국어 2~3자 trigram 재현율 부족 | 검색 품질 저하 | prefix ILIKE 병행, 시드로 재현율 측정, FTS·Meilisearch는 3차 검토 |
| GIN 쓰기 비용 | 재고 저장 지연 | 갱신 핫패스 측정, 설명 인덱스는 선택 적용 |
| ACL `EXISTS` 비용 | 검색 지연 | M6.0은 접근 ID 주입으로 시작하고, p95 실패 시 `SearchAclMapper` EXISTS와 인덱스를 적용 |
| ACL 의미 불일치 | 데이터 노출 또는 누락 | `SearchAclParityTest`를 M6.0부터 유지하고 SQL 최적화 전후로 통과 |
| M5.5 gate 미완료 | 검색 계약·이미지 ID 재작업 | M5.5 P0/P1 완료와 계약 동결 후 M6.0 착수 |
| M6.0 성능 미달 | UI 재작업 | M6.0 go/no-go, 인덱스·쿼리·읽기 모델 순서로 대응 |
| 그룹 집계 SQL 복잡도 | p95 초과 | CTE별 EXPLAIN, 읽기 모델은 최후 수단 |
| 커서 지문 불일치 | 필터 변경 시 오류 | 클라이언트가 필터 변경 시 커서를 버리도록 강제 |
| `inventoryCount`·`matchedInventoryCount` 혼동 | UI 숫자 불일치 | M6는 같은 값, 계약 문구 명확화 |
| 조회 기록 누락 | 자주 본 물건 미동작 | M6.0 계약에 포함하고 `ItemViewApiTest`로 고정 |
| 시드와 운영 규모 차이 | p95 오측정 | 운영 규모 2배까지 추가 측정, 통계 갱신 |
| 위치 이름 변경 | 경로 불일치 | 위치 조인으로 최신 이름 사용, 읽기 모델은 갱신 이벤트 |

## 12. 확인 필요, ADR, M7 연결

### 12.1 확인 필요

| 항목 | 권장안 |
|---|---|
| `inventoryCount` 기준과 `matchedInventoryCount` | [검색](../search.md)대로 일치 수를 쓰고 `matchedInventoryCount`는 같은 값. 전체 목록 API와의 차이는 M7 전에 계약 재확인 |
| 빈 q의 `RELEVANCE` | `UPDATED_DESC`로 대체하고 커서에 대체 정렬 기록 |
| 그룹 `primaryImageId` | Item 마스터 대표 이미지 우선, 없으면 그룹 내 첫 Inventory 대표 이미지 |
| 위치 필터 2개 이상 | `400 VALIDATION_FAILED` |
| FTS·`inventory_search` 도입 | trigram p95·재현율 측정 후 결정, 읽기 모델은 두 번 연속 실패 시 ADR |
| 폐기만 있는 Item의 자주 본 물건 | 기본 제외, 명시 필터에서만 표시 |
| 커서 서명·TTL, 이름 정렬 collation | M6는 지문만 사용, 운영·테스트 collation 고정 |

### 12.2 ADR

- [ADR-019](../decisions/ADR-019-search-acl-pagination.md): 검색 ACL, 커서·Item 그룹 페이지네이션, 읽기 모델 gate. M6.0은 접근 ID 주입, p95 실패 시 SQL EXISTS 최적화, `SearchAclParityTest` 유지, `inventory_search`는 두 번 연속 p95 실패 전까지 도입하지 않는다.
- ADR-017·018은 [PLAN-006](PLAN-006-media-messaging.md)의 이미지 후처리·보관 정책용으로 예약된 번호이며, M6 검색 ADR은 019로 고정한다.

### 12.3 M7 연결

M7 상세 계획은 [PLAN-008 AI·이미지 검색](PLAN-008-ai-image-search.md)입니다. M7 이미지 검색은 M6의 `SearchFilter`, `SearchCursor`, 그룹 응답 조립을 재사용합니다.

- `POST /search/image`는 같은 필터·정렬·그룹핑, `limit` 최대 50, 5초 타임아웃, `503 IMAGE_SEARCH_UNAVAILABLE`을 적용합니다.
- 후보는 `limit * 5`, 최대 200개를 가져온 뒤 ACL을 적용합니다([검색](../search.md) 13번 항목, [운영](../operations.md)의 `search.candidate-multiplier`·`search.candidate-max`).
- 임베딩 모델·버전 필터와 fallback 금지는 [ADR-012](../decisions/ADR-012-embedding-versioning.md)를 따릅니다.
- M6에서 SCR-003 이미지 검색 진입점을 비활성으로 두고 M7에서 `features/search/image-search-dialog.tsx`와 같은 결과 카드 UI를 재사용합니다. M6 종료 시 `SearchGroupAssembler`와 필터 DTO의 재사용성을 리뷰합니다.

## 13. M6 보완 실행 기록 (2026-10-23)

기존 ADR-019와 이 계획의 4번 항목을 구현합니다. 현재 500개 후보 제한과 앱의 offset 그룹핑을 제거하고, SQL에서 전체 일치 수를 집계한 뒤 Item 그룹 `limit + 1`과 그룹별 최대 6개를 조회합니다. 정렬 키와 Item ID, 필터 지문을 담은 커서를 사용하며 ACL은 기존 접근 ID 주입을 유지합니다.

| 순서 | 작업 | 검증 |
|---|---|---|
| 1 | 누락·중복·잘못된 커서·그룹 개수 회귀 테스트 추가 | 기존 구현에서 실패 확인 |
| 2 | SQL 그룹 페이지와 정렬 5종 keyset cursor 구현 | 통합 테스트, ktlint·detekt |
| 3 | 재현 가능한 10,000 Item·50,000 Inventory 성능 검증 | 워밍업 5회·측정 20회, p95·EXPLAIN 기록 |
| 4 | 계약·생성 타입·문서 상태 대조 | 계약 생성, 문서 검사, 전체 회귀 테스트 |

검토 초점은 한 Item의 재고가 500개를 넘는 경우, 동일 정렬 키, 페이지 사이의 새 그룹 추가·삭제, 필터를 바꾼 커서 재사용, 공유 철회 후의 커서 재사용입니다. 변경은 `fix/search-release-gates` 브랜치에서 순차 구현합니다.

### 13.1 검증 현황

- 검색 통합 테스트 12개, 커서 단위 테스트 3개, OpenAPI 계약 테스트 2개 통과.
- 프론트엔드 테스트 37개와 모바일·데스크톱 검색 E2E 통과.
- 최초 그룹 집계 구현의 p95는 전체 20그룹 1,061ms·100그룹 1,479ms로 실패했습니다. 삭제 필터의 행 수 오추정, 후보 임시 결과 재탐색, 전 후보 이미지 조회와 JIT 비용을 실제 EXPLAIN으로 확인했습니다.
- 이미지 조회를 페이지 뒤로 옮긴 2026-10-23 로컬 측정에서 전체 20그룹 p95 194.68ms, 100그룹 254.51ms, 이름 부분 검색 299.46ms, 공유 필터 34.15ms, 사진 필터 230.28ms를 기록했습니다. p99는 모두 600ms 이하였습니다.
- 독립 리뷰에서 실제 검색 SQL 대신 별도 Item 쿼리의 인덱스를 검사하는 검증 공백을 발견해 실제 MyBatis SQL의 GIN 사용을 검사하도록 보강했습니다. PostgreSQL 시간대 범위 밖 커서도 요청 단계에서 거부합니다.
- 보강된 성능 gate와 백엔드 전체 회귀 테스트를 재확인 중입니다. 위 수치는 로컬 단일 사용자 측정이며 VPS 동시 부하 검증은 별도입니다.
- API 타입은 기존 ADR-015의 미리보기 모드를 유지합니다. 생성물 기록 모드 전환과 추천·최근 검색 잔여 검증은 별도 마감 항목입니다.

- 기존 로컬 DB에는 이 브랜치에 없는 `V19__inventory_access_view.sql`이 적용되어 있어 이력이 다릅니다. 릴리스 브랜치의 V19~V23과 충돌을 피하려고 설명 인덱스는 V24로 배정했습니다. 기존 DB를 repair하거나 이력을 수정하지 않고 깨끗한 임시 DB에서 V1~V18·V24 적용과 데이터 사전 생성을 검증합니다. 로컬 DB를 이 브랜치로 기동하려면 V19 이력의 출처를 먼저 맞춰야 합니다.
