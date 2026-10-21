# PLAN-004: M3 카테고리·Item·Inventory 상세 구현 계획

> 상태: M3 핵심 구현 완료 (2026-10-21, 후속 보완 항목은 13번 참조)
> 역할: 구현 로드맵 M3를 데이터 모델, 서비스, API, 화면, 테스트, 완료 조건으로 분해한 실행 계획
> 상위 문서: [구현 로드맵](../implementation-roadmap.md)
> 선행 문서: [PLAN-001 기반](PLAN-001-foundation.md), [PLAN-001 계약·CI·검증](PLAN-001-foundation-delivery.md), [PLAN-003 계층·권한](PLAN-003-hierarchy-permissions.md)
> 관련 문서: [도메인](../domain.md), [공유](../sharing.md), [검색](../search.md), [계약](../contracts.md), [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md), [데이터 사전](../generated/data-dictionary.md), [OpenAPI](../generated/openapi.yaml), [ADR-002](../decisions/ADR-002-inventory-location.md), [ADR-003](../decisions/ADR-003-permission-model.md), [ADR-004](../decisions/ADR-004-mybatis.md), [ADR-009](../decisions/ADR-009-ai-confirmation.md), [ADR-010](../decisions/ADR-010-item-creation-permissions.md), [ADR-011](../decisions/ADR-011-image-upload-lifecycle.md), [ADR-016](../decisions/ADR-016-neo-brutal-design-system.md)
> 전제: 2026-10-21 현재 M0-M2 완료. M1은 [PLAN-002](PLAN-002-identity-space.md), M2는 [PLAN-003](PLAN-003-hierarchy-permissions.md)으로 구현·검증되었고, M3는 V6부터 시작합니다.

## 1. 목적, 범위, 비목표

M3의 목적은 소유자가 카테고리와 Item 마스터를 만들고, 첫 Inventory를 등록한 뒤, 재고별 오버라이드·상태·위치를 관리하고 이동 이력을 확인하는 첫 수직 슬라이스를 완성하는 것입니다.

| 구분 | 포함 |
|---|---|
| 마스터 | Category 생성·이름 변경·삭제·병합, Item 생성·수정·삭제 |
| 재고 | Inventory 생성, 첫 Inventory 동반 생성, 오버라이드, 상태, 삭제 |
| 유효 값 | effective name·description·category·price와 오버라이드 해제 |
| 상태 | `IN_USE`, `STORED`, `DISPOSED`, `confirmDisposal` |
| 위치 | Room·Shelf·Box 직접 위치, 이동, 같은 위치 no-op |
| 이력 | `inventory_movement` 기록, 이동 이력 API·타임라인 |
| 동시성·멱등성 | Item·Inventory `version`, 생성 API `Idempotency-Key` |
| M2 연동 | Room·Shelf·Box `inventoryCount`, `HierarchyDeleteGuard`의 재고 차단 |
| 화면 | SCR-008·009·010·011·012, SCR-017의 카테고리 관리 일부 |

비목표는 다음과 같습니다.

- 공유 Grant·초대와 공유 사용자의 Inventory 추가·삭제·이동은 M4입니다. M3는 소유자 흐름과 M4용 seam만 만듭니다.
- 이미지 presign·후처리·대표 이미지·Inventory 이미지는 M5입니다. M3의 `primaryImageId`는 항상 `null`입니다.
- 검색 랭킹·최근 검색·자주 본 물건·Item 그룹 커서는 M6입니다. M3는 목록 필터와 Item 선택용 단순 조회만 제공하며, AI·상태 변경 이력·감사 로그 UI·수량·다중 통화·오프라인 편집은 제외합니다.
- `outbox_event`, `image`, `share`, `audit_log` 테이블은 만들지 않습니다. 이벤트 시점은 9번 항목에서 결정합니다.

M3 완료의 정의는 카테고리 → Item + 첫 Inventory → 오버라이드·상태 수정 → 이동 → 이력 확인이 한 흐름으로 동작하고, M2 계층 화면의 재고 개수·삭제 차단과 8·10번 항목의 검증을 통과한 상태입니다.

## 2. 선행 조건 M0-M2

| 마일스톤 | M3가 요구하는 산출물 | 확인 |
|---|---|---|
| M0 (완료) | Gradle·pnpm 빌드, Testcontainers, Flyway, problem+json, `request_id`, 디자인 토큰 | `./gradlew :backend:test`, `pnpm build` |
| M1 | 세션 인증, `Actor`·Space 컨텍스트, CSRF, `app_user`·`space`·`V2__identity.sql`, 공통 오류 코드 | 인증 통합 테스트, `/auth/me` |
| M2 | [PLAN-003](PLAN-003-hierarchy-permissions.md)의 `V4__hierarchy.sql`·`V5__idempotency.sql`, `AccessService`, `GrantLookup`, `HierarchyDeleteGuard`, `effectivePermission`, `version` 패턴 | M2 완료 조건, 계층·권한·교차 Space 테스트 |

착수 게이트는 다음과 같습니다.

- M1 `V2`, M2 `V4`·`V5`가 적용된 전제에서 M3는 `V6`부터 시작합니다. 마이그레이션 번호는 M2 head(V5) 다음으로 확정했습니다.
- M2의 `AccessService.require(actor, AccessTarget, minimum)`와 `effectivePermission`/`resolveAll`을 그대로 사용합니다. Inventory 권한은 위치한 Room·Shelf·Box의 `AccessTarget`으로 계산하고, Item·Category 마스터 권한은 `requireOwner(actor, spaceId)`로 판정합니다. 별도 `requireInventory` ACL을 만들지 않습니다.
- M2의 `common.idempotency` 서비스와 `idempotency_record`를 재사용합니다. M3 컨트롤러는 `IdempotencyService.execute`를 호출하고, 테이블과 저장 로직을 새로 만들지 않습니다.
- M3는 `HierarchyDeleteGuard`에 Inventory 존재 검사를 넣고 Room·Shelf·Box 응답에 `inventoryCount`를 추가합니다. M2 12번 항목에서 넘긴 작업입니다.
- 세션의 `Actor`와 `spaceId`만 사용하며 어떤 요청도 `spaceId`를 받지 않습니다. 공통 `Problem.meta`와 오류 코드 enum을 확장할 수 있어야 합니다.

### 2.1 착수 게이트 확인 결과

| 게이트 | 현재 상태 |
|---|---|
| `AccessService` target 기반 판정 | `AccessTarget`, `effectivePermission(actor,target)`, `require`, `resolveAll`, `isShared` 구현·테스트 완료 |
| `GrantLookup` 포트 | `NoGrantLookup`으로 M3 소유자 흐름 지원, M4에서 SQL 구현으로 교체 |
| M2 멱등성 | `IdempotencyService`와 advisory lock 동시성 테스트 완료 |
| M2 삭제 잠금 | Room/Shelf/Box 부모 `FOR UPDATE` 적용 |
| 마이그레이션 | M2 V4·V5 적용 확인, M3는 V6부터 시작 |
| 계약 | OpenAPI preview 18 paths, design draft 0.3.0, M3 상세 계약은 구현 시 확장 |

## 3. 데이터 모델과 마이그레이션

전체 컬럼·타입·제약의 단일 원본은 [데이터 사전](../generated/data-dictionary.md)입니다. 이 절은 M3 테이블과 데이터 사전 대비 확정·추가 항목만 기록하며 전체 DDL을 다시 쓰지 않습니다.

| 테이블 | 마이그레이션 | 핵심 컬럼(타입) | M3 제약·인덱스 |
|---|---|---|---|
| `category` | `V6__category.sql` | `id uuid PK`, `space_id`, `name text`, `normalized_name text`, `color text NULL`, `sort_order integer`, `deleted_at` | Space FK, `UNIQUE (space_id, normalized_name) WHERE deleted_at IS NULL`, 활성 목록 인덱스 |
| `item` | `V7__item.sql` | `id`, `space_id`, `name text`, `description text NULL`, `category_id uuid NULL`, `price_amount integer NULL`, `currency char(3)`, `version bigint`, `deleted_at` | `CHECK (price_amount >= 0)`, `UNIQUE (id, space_id)`, `(category_id, space_id)` 복합 FK |
| `inventory` | `V8__inventory.sql` | `id`, `space_id`, `item_id`, 위치 3종, `name_override`, `description_override`, `category_id_override`, `price_amount_override`, `status`, `version bigint`, `deleted_at` | 위치 3컬럼 중 1개 CHECK, Item·위치 3종·카테고리 복합 FK, 가격 CHECK |
| `inventory_movement` | `V9__inventory_movement.sql` | `id`, `space_id`, `inventory_id`, `from_*`·`to_*` 위치 3종, `moved_by`, `moved_at` | from·to 각 1개 CHECK, Inventory·위치 6종 복합 FK, 페이지 인덱스 |
| `idempotency_record` | M2 `V5`, M3 재사용 | `user_id`, `idempotency_key`, `request_method`, `request_path`, `request_hash char(64)`, `response_status`, `response_body jsonb`, `expires_at` | `UNIQUE (user_id, idempotency_key)`, `(expires_at)` |

M3에서 추가할 인덱스는 다음과 같습니다. 기존 데이터 사전 인덱스는 유지합니다.

| 인덱스 | 정의 | 목적 |
|---|---|---|
| `idx_item_list_active` | `(space_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL` | Item 목록 커서 |
| `idx_inventory_list_active` | `(space_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL` | Inventory 목록 커서 |
| `idx_inventory_status_active` | `(space_id, status, updated_at DESC, id DESC) WHERE deleted_at IS NULL` | 기본 상태 필터 |
| `idx_inventory_item_active` | `(space_id, item_id) WHERE deleted_at IS NULL` | Item 상세 재고 목록 |
| `idx_inventory_movement_page` | `(space_id, inventory_id, moved_at DESC, id DESC)` | 이동 이력 커서 tie-breaker |

- `category` → `item` → `inventory` → `inventory_movement` 순서로 FK를 만족합니다. `idempotency_record`와 만료 인덱스는 M2 것을 다시 만들지 않습니다.
- M3는 `inventory` 도메인의 첫 데이터 생성이므로 backfill이 없습니다. 개발용 시드는 마이그레이션과 분리합니다.
- 위치 CHECK와 복합 FK는 데이터 사전 정의를 그대로 쓰고 애플리케이션 검증으로 대체하지 않습니다.
- `item.currency`는 `KRW`만 허용합니다. 애플리케이션 검증과 DB `CHECK (currency = 'KRW')`를 함께 둘 것을 권장하며, 데이터 사전 갱신 대상입니다.
- 효과 필드 저장 컬럼은 만들지 않고 읽기 시 `COALESCE`로 계산합니다.
- `inventory_movement`는 불변이며 최초 등록 행을 만들지 않습니다. UI가 `created_at`과 첫 이동의 `from`으로 "등록"을 합성합니다.

## 4. 도메인·서비스 설계

권한의 단일 원본은 [공유](../sharing.md), 상태·위치·카테고리 규칙은 [도메인](../domain.md)입니다. 이 절은 M3의 계산 순서와 트랜잭션 경계만 정합니다.

### 4.1 효과 필드

| 필드 | 계산 | 해제 |
|---|---|---|
| 이름 | `name_override ?? item.name` | `nameOverride: null` |
| 설명 | `description_override ?? item.description` | `descriptionOverride: null` |
| 카테고리 | `category_id_override ?? item.category_id` | `categoryIdOverride: null` |
| 가격 | `price_amount_override ?? item.price_amount` | `priceAmountOverride: null` |
| 통화 | 항상 `item.currency` | 통화 오버라이드 없음 |

- 이름 오버라이드의 빈 문자열은 422로 거부하고 설명의 빈 문자열은 명시값으로 허용할 것을 권장합니다. 이 항목은 **확인 필요**입니다.
- 가격 `0`은 명시값이고 마스터·오버라이드가 모두 `null`이면 `effectivePrice`는 `null`입니다.
- 목록·상세는 Item·Category 조인 단일 SQL에서 `COALESCE`로 계산하고 Kotlin에서 다시 계산하지 않습니다.
- `overriddenFields`는 `NAME`, `DESCRIPTION`, `CATEGORY`, `PRICE` 중 설정된 필드이며 UI 배지·편집 폼이 사용합니다. Category 조인이 비면 효과 카테고리는 `null`, 표시는 `미분류`입니다.

### 4.2 오버라이드와 JSON Merge Patch

| 요청 키 | 생략 | `null` | 값 |
|---|---|---|---|
| `nameOverride` | 유지 | 해제 | 새 이름, 빈 문자열 422 권장 |
| `descriptionOverride` | 유지 | 해제 | 새 설명, 빈 문자열 허용 |
| `categoryIdOverride` | 유지 | 해제 | 같은 Space의 활성 Category |
| `priceAmountOverride` | 유지 | 해제 | 0 이상 정수 |
| `status` | 유지 | 422 | `IN_USE`, `STORED`, `DISPOSED` |
| `confirmDisposal` | false | 422 | `true`만 허용 |
| `version` | 422 | 422 | 현재 값과 같은 정수 |

- `version`은 패치 대상이 아니라 사전 조건이며 본문에 필수이고 저장하지 않습니다. `price`는 원자적 객체라 `{ "amount": 100 }` 같은 부분 객체는 422입니다.
- 변경 필드가 없으면 버전을 올리지 않고 200을 반환합니다. 단 `version`이 오래되었으면 변경이 없어도 409입니다.
- `confirmDisposal`은 `DISPOSED` 전환에서만 유효하고 다른 status와 오면 422입니다. 이미 `DISPOSED`인 재고에 같은 상태를 다시 보내는 것은 확인 없는 no-op입니다.

### 4.3 마스터 수정 전파

마스터 수정은 Inventory 행을 갱신하지 않습니다. 읽기 시 `COALESCE`가 새 마스터 값을 반영하므로 오버라이드가 없는 재고에 자동 전파됩니다.

| `update-impact` 필드 | 의미 |
|---|---|
| `affectedInventoryCount` | 변경 필드 중 하나라도 오버라이드가 없어 표시가 바뀌는 활성 Inventory |
| `protectedByOverrideCount` | 변경 필드 중 하나라도 오버라이드가 있어 값이 유지되는 활성 Inventory |
| `byField` | 필드별 `{affected, protected}` |

- 두 수는 배타적이지 않으며 삭제되지 않은 Inventory 전체를 세고 `DISPOSED`도 포함합니다. UI에 "폐기 포함"을 표시합니다.
- 마스터 수정은 Item `version`만 올리고 Inventory `version`·`updated_at`은 유지합니다.
- 서버는 확인 플래그 없이 영향 API만 제공합니다. 서버 강제가 필요하면 **확인 필요**이며 권장안은 현재 방식입니다. 수정 후 Item·목록·Inventory 쿼리를 무효화합니다.

### 4.4 상태와 폐기

| 전이 | 허용 | 확인 |
|---|---|---|
| `IN_USE` ↔ `STORED` | 예 | 없음 |
| `IN_USE`/`STORED` → `DISPOSED` | 예 | `confirmDisposal=true` |
| `DISPOSED` → `IN_USE`/`STORED` | 예 | 없음 |
| 같은 상태 | 예, no-op | 없음 |

- 생성 시 상태를 생략하면 `STORED`입니다. `DISPOSED`는 삭제가 아니며 기본 목록은 `IN_USE`·`STORED`만 조회하고 `status=DISPOSED`일 때만 폐기를 포함합니다.
- 상태 변경은 Inventory `version`을 올리고 위치·임베딩·이미지는 바꾸지 않습니다. 상태 변경 이력은 3차 범위입니다.

### 4.5 이동, no-op, 이력

1. 세션 `spaceId`와 `inventoryId`로 Inventory와 위치 조상을 조회합니다. 없거나 다른 Space면 404입니다.
2. 위치 노드의 `AccessTarget`으로 `AccessService.require(actor, target, MANAGE)`를 호출하고 요청 `version`을 확인합니다. 불일치는 409입니다.
3. target 유형·ID로 같은 Space의 활성 위치를 조회하고 target에도 `MANAGE`를 적용합니다. 없거나 삭제면 404, 유형 불일치는 422입니다.
4. 현재 위치와 같으면 200 no-op이며 버전·이력·이벤트를 만들지 않습니다.
5. 다르면 위치 3컬럼 중 target 하나만 설정하고 나머지를 `NULL`로 만든 뒤 `version = version + 1`로 갱신합니다.
6. 같은 트랜잭션에서 `inventory_movement`에 `from`·`to`·`moved_by`·`moved_at`을 기록합니다.

| 상황 | 응답 | 버전 | 이력 |
|---|---|---|---|
| 같은 위치 | 200 현재 재고 | 증가 없음 | 없음 |
| 다른 위치 | 200 갱신 재고 | +1 | 1행 |
| 오래된 version | 409 | 변경 없음 | 없음 |
| target 없음·다른 Space | 404 | 변경 없음 | 없음 |

- 이력은 `(moved_at DESC, id DESC)` 커서로 페이지네이션하고, 타임라인은 최신순과 맨 아래 "등록" 합성으로 구성합니다. 이동이 없으면 현재 위치가 최초 위치입니다.
- 위치 이름은 조회 시점 이름을 조인하며 스냅샷은 저장하지 않습니다. 필요하면 **확인 필요**이고 권장안은 M3 ID 저장·M6 보강입니다. M3는 이동 이벤트를 발행하지 않습니다.

### 4.6 카테고리 정규화와 병합

- 표시 이름은 앞뒤 공백 제거·연속 공백 축소, 정규화 이름은 Unicode NFKC·소문자화·공백 정리를 적용합니다.
- 생성·수정·삭제·병합은 `SpaceOwnership.requireOwner`를 통과한 소유자만 수행합니다. 정규화 이름 중복은 409 `CATEGORY_ALREADY_EXISTS`, 빈 이름·80자 초과는 422입니다.
- 참조하는 활성 Item·Inventory가 있으면 삭제는 409 `CATEGORY_IN_USE`이며 병합을 안내합니다.
- 병합은 source·target을 ID 오름차순으로 `SELECT ... FOR UPDATE`한 뒤 한 트랜잭션에서 `item.category_id`와 `inventory.category_id_override`를 target으로 갱신하고, 영향 행의 `version`을 올린 뒤 source를 soft delete합니다.
- 자기 자신, 삭제된 target, 다른 Space target은 거부합니다. 응답은 204 대신 200과 `mergedItemCount`·`mergedInventoryCount`를 권장하며 **확인 필요**입니다. M3는 소유자 전체 목록만 제공하고 공유 범위는 M4에서 연결합니다.

### 4.7 트랜잭션 경계

| 작업 | 트랜잭션 | 잠금·동시성 | 부수 효과 |
|---|---|---|---|
| Item + 첫 Inventory 생성 | 1개 | M2 멱등성 필터 | Item, Inventory, idempotency_record |
| 기존 Item에 Inventory 생성 | 1개 | M2 멱등성 필터, Item 확인 | Inventory, idempotency_record |
| Item 마스터 PATCH | 1개 | `WHERE version = ?` | Item version +1 |
| Inventory PATCH | 1개 | `WHERE version = ?` | Inventory version +1 |
| Inventory 이동 | 1개 | version 갱신 후 movement insert | version +1, movement 1행 |
| Inventory·Item 삭제 | 1개 | 참조 확인, soft delete | `deleted_at` |
| Category 병합 | 1개 | source·target `FOR UPDATE` | Item·Inventory 갱신, source soft delete |

- 트랜잭션 안에서 외부 AI·스토리지·메일을 호출하지 않으며 실패한 생성은 idempotency_record를 남기지 않습니다. 만료 정리는 M2 배치를 재사용합니다.
- `UPDATE ... WHERE version = ?`가 0행이면 같은 트랜잭션에서 존재 여부를 다시 조회해 404와 409를 구분합니다.

## 5. API 계약

기본 규칙·인증·오류·페이지네이션은 [계약](../contracts.md)을 따릅니다. Base path는 `/api/v1`이고 상태 변경은 CSRF 토큰을 요구합니다.

### 5.1 엔드포인트

| Method | Path | 권한 | version | Idempotency-Key | 비고 |
|---|---|---|---|---|---|
| GET | `/categories` | 인증 | 없음 | 없음 | M3는 소유자 전체 |
| POST | `/categories` | OWNER | 없음 | 지원 | 중복 409 |
| PATCH | `/categories/{categoryId}` | OWNER | 없음 | 없음 | 이름·색·정렬 |
| DELETE | `/categories/{categoryId}` | OWNER | 없음 | 없음 | 사용 중 409 |
| POST | `/categories/{categoryId}/merge` | OWNER | 없음 | 없음 | 200·건수 권장 |
| GET | `/items` | 접근 범위 | 없음 | 없음 | `q`, `categoryId`, `scope`, `sort` |
| POST | `/items` | OWNER | 없음 | 지원 | 마스터만 생성 |
| GET | `/items/{itemId}` | 접근 범위 | 없음 | 없음 | 마스터·재고 수 |
| PATCH | `/items/{itemId}` | OWNER | 필수 | 없음 | 마스터 수정 |
| DELETE | `/items/{itemId}` | OWNER | 없음 | 없음 | 재고 있으면 409 |
| GET | `/items/{itemId}/inventories` | 접근 범위 | 없음 | 없음 | `status` 반복 |
| GET | `/items/{itemId}/update-impact` | OWNER | 없음 | 없음 | `fields` 반복 |
| GET | `/inventories` | 접근 범위 | 없음 | 없음 | 필터·커서 |
| POST | `/inventories` | 기존 Item MANAGE, 신규 Item OWNER | 없음 | 지원 | `itemId` 또는 `newItem` 1개 |
| GET | `/inventories/{inventoryId}` | 접근 권한 | 없음 | 없음 | 효과 값·오버라이드 |
| PATCH | `/inventories/{inventoryId}` | EDIT 이상 | 필수 | 없음 | 오버라이드·상태 |
| DELETE | `/inventories/{inventoryId}` | MANAGE 이상 | 없음 | 없음 | soft delete |
| PATCH | `/inventories/{inventoryId}/location` | source·target MANAGE | 필수 | 없음 | 같은 위치 no-op |
| GET | `/inventories/{inventoryId}/move-targets` | source MANAGE | 없음 | 없음 | 이동 가능 위치 |
| GET | `/inventories/{inventoryId}/movements` | VIEW 이상 | 없음 | 없음 | cursor·limit |

- M3의 쓰기 통과자는 사실상 OWNER이며 M2 `Permission`의 `OWNER`를 그대로 씁니다. `POST /items`와 `/inventories`의 `newItem`은 같은 `ItemService.create`를 호출합니다.
- `POST /inventories`는 `itemId`와 `newItem`이 모두 있거나 모두 없으면 422입니다. DELETE는 M2와 같이 `version` 없이 참조 확인·soft delete를 처리합니다.

### 5.2 응답 필드 델타

| 스키마 | 추가·변경 | 이유 |
|---|---|---|
| `Inventory` | `overrides`, `overriddenFields`, `effectivePermission`, nullable `effectiveCategory`·`effectivePrice` | 편집 폼·배지·권한 UI |
| `Item` | `effectivePermission`, `inventoryCount`의 `DISPOSED` 기준 문서화 | 소유자 버튼, 삭제 가능 조건 |
| `Category` | `itemCount`, `inventoryCount` | 카테고리 관리·병합 |
| `ItemUpdateImpact` | `byField`, `fields` 쿼리 | 필드별 전파 범위 |
| `Room`·`Shelf`·`Box` | `inventoryCount` | M2 계층 화면·삭제 차단 |
| `Problem` | `meta.inventoryCount`, `meta.itemCount` | 삭제·병합 차단 사유 |

`inventoryCount`는 삭제되지 않은 Inventory 전체를 세고 `DISPOSED`를 포함할 것을 권장합니다. 기본 목록은 폐기를 숨기지만 Item 삭제는 폐기 재고도 차단하므로 상세 수와 삭제 조건이 일치합니다. 이 항목은 **확인 필요**입니다.

### 5.3 PATCH와 version

- `PATCH /items/{itemId}`, `/inventories/{inventoryId}`, `/inventories/{inventoryId}/location`은 본문에 `version`을 필수로 받고 불일치는 409 `VERSION_CONFLICT`입니다. 성공 응답의 `version`은 증가한 값입니다.
- 프론트엔드는 폼에 `version`을 보관하고 409에서 최신 리소스를 재조회합니다.
- `application/json`과 `application/merge-patch+json`을 모두 받으며 미디어 타입에 따라 동작을 바꾸지 않습니다.

### 5.4 Idempotency-Key

M2의 `common.idempotency` 필터·서비스와 `idempotency_record`를 그대로 사용합니다. M3는 `POST /categories`, `/items`, `/inventories`를 대상 경로에 추가하고 아래 동작을 테스트합니다.

- 헤더는 선택이며 1~255자이고 없으면 일반 생성, 공백만 있으면 400입니다. 요청 해시는 method·path·정규화 본문의 SHA-256입니다.
- 같은 키·같은 해시면 저장된 상태·본문과 `Location`을 재생하고, 같은 키·다른 해시면 409 `IDEMPOTENCY_KEY_REUSED`입니다.
- 기록이 없으면 도메인 쓰기와 기록 insert를 같은 트랜잭션에서 수행합니다. 2xx만 저장하고 4xx·5xx는 저장하지 않아 같은 키로 재시도할 수 있으며, 보존은 24시간이고 M2 만료 배치를 재사용합니다.
- 키는 사용자별로 구분됩니다. 동시 요청 직렬화는 M2 구현을 따르고 M3에서 새 락·테이블을 추가하지 않으며, M2가 중복 생성을 막지 못하면 M2 보완 항목으로 올립니다. 이 항목은 **확인 필요**입니다.

### 5.5 오류 코드

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 문법, 알 수 없는 필드, 잘못된 키 |
| 403 | `PERMISSION_DENIED` | 비소유자의 Item 생성·마스터 수정·카테고리 관리 |
| 404 | `RESOURCE_NOT_FOUND` | 없음, 삭제, 다른 Space, 접근 범위 밖 |
| 409 | `VERSION_CONFLICT` | PATCH·이동 version 불일치 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 키로 다른 요청 |
| 409 | `CATEGORY_ALREADY_EXISTS` | 정규화 이름 중복 |
| 409 | `CATEGORY_IN_USE` | 사용 중 Category 삭제, `meta` 건수 |
| 409 | `ITEM_HAS_INVENTORIES` | 재고가 남은 Item 삭제, `meta.inventoryCount` |
| 422 | `DISPOSAL_CONFIRMATION_REQUIRED` | 확인 없는 `DISPOSED` 전환 |
| 422 | `CURRENCY_NOT_SUPPORTED` | KRW 이외 통화 |
| 422 | `VALIDATION_FAILED` | `itemId`/`newItem` 조합, 부분 price, 빈 이름, 자기 병합, 위치 유형 |

- 403·404 구분은 [계약](../contracts.md) 기준을 따르고 존재를 숨겨야 하면 404입니다. 차단 건수는 `Problem.meta`에 담고 `fieldErrors`를 재사용하지 않으며 새 코드는 계약과 OpenAPI에 함께 추가합니다.

### 5.6 목록 필터

| 필터 | M3 동작 |
|---|---|
| `status` | 반복 파라미터, 생략 시 `IN_USE`·`STORED`, `DISPOSED`는 명시 시만 |
| `categoryId` | 효과 카테고리 기준 |
| `roomId` / `shelfId` / `boxId` | 하위 포함 / 하위 Box 포함 / 해당 Box만 |
| `scope` | `ALL`, `OWNED`, `SHARED`이며 M3 `SHARED`는 빈 결과 |
| `hasImage` | M3 미지원, 값이 오면 422 |
| `q` | `/items`만 단순 `ILIKE`, M6에서 대체 |
| `sort` | `UPDATED_DESC`, `NAME_ASC`, `STATUS_ASC`, `VIEWED_DESC`는 M3 422 |
| `limit`·`cursor` | 기본 20·최대 100, 잘못된 커서는 400 |

- `hasImage`·`VIEWED_DESC`는 M3에 데이터 원천이 없어 422로 거부하거나 M5·M6까지 OpenAPI에서 숨길 것을 권장하며 **확인 필요**입니다. 목록의 `primaryImageId`는 항상 `null`입니다.

## 6. 프론트엔드

라우팅은 `frontend/src/app/router.tsx`에 추가하고 API 타입은 `pnpm generate:api`로 `frontend/src/shared/api/schema.d.ts`를 재생성합니다. M2의 Radix Dialog·AlertDialog, React Hook Form, Zod를 재사용하고 새 UI 라이브러리를 추가하지 않습니다.

| 화면 | 경로 | 핵심 |
|---|---|---|
| SCR-008 재고 목록 | `/inventory` | 상태·카테고리·위치 필터, 정렬, 커서 |
| SCR-009 재고 상세 | `/inventory/:inventoryId` | 히어로, 정보 표, 오버라이드 배지, 이동 이력 |
| SCR-010 재고 편집 | `/inventory/:inventoryId/edit` | 오버라이드·상태·폐기 확인 |
| SCR-011 물품 생성 | `/inventory/new` | 신규 Item + 첫 Inventory, 기존 Item 추가 |
| SCR-012 물품 상세 | `/items/:itemId` | 마스터, 연결 재고, 수정 영향 |
| SCR-017 일부 | `/settings/categories` | 카테고리 생성·이름 변경·병합·삭제 |

M2의 Room·Shelf·Box 상세는 M3에서 `inventoryCount`와 직접 위치 재고 목록, "재고 추가" 진입점을 연결합니다. 새 화면이 아니라 M2 화면의 빈 상태와 액션을 채우는 작업입니다.

- SCR-008: 상태 칩(사용중·보관·폐기), 카테고리·위치 선택, 정렬. 기본은 `IN_USE`·`STORED`이고 폐기는 직접 켭니다. 행은 썸네일 플레이스홀더, 효과 이름, 위치 경로, 상태, 오버라이드 배지를 표시합니다. "더 보기"는 `nextCursor`를 이어 붙이고 필터 변경 시 커서를 초기화합니다.
- SCR-009: 브레드크럼, 히어로 플레이스홀더, 이름·재고 수·상태. 정보 표는 오버라이드 행에 `재고값` 배지와 마스터 값을 붙입니다. 이동은 `move-targets` 트리에서 선택하고 같은 위치면 요청 없이 닫으며, 편집·이동은 권한으로 숨깁니다. 이력은 최신순 타임라인과 "등록" 항목으로 구성하고 데스크톱은 같은 Item의 다른 재고를 표시합니다.
- SCR-010: 오버라이드 4종과 상태를 편집합니다. 토글을 끄면 `null`을 보내고, `DISPOSED`는 `AlertDialog` 확인 후 `confirmDisposal: true`를 보냅니다. 409이면 최신 데이터를 재조회하고 입력은 유지하되 자동 병합하지 않습니다. 이미지 영역은 M5 전까지 숨깁니다.
- SCR-011: "새 물품"과 "기존 물품에 재고 추가" 두 모드입니다. 새 물품은 이름·설명·카테고리·가격·첫 위치·선택적 오버라이드를 `newItem`으로, 기존 모드는 Item 선택과 `itemId`로 보냅니다. 신규 Item·새 카테고리 버튼은 OWNER만 노출하고, `crypto.randomUUID()` 키를 성공까지 재사용합니다.
- SCR-012: 마스터 카드, 연결 재고, "재고 추가", 수정·삭제. 삭제는 재고가 있으면 409 `ITEM_HAS_INVENTORIES`로 안내합니다. 마스터 수정 전 `update-impact?fields=...`의 `affectedInventoryCount`·`protectedByOverrideCount`·`byField`를 대화상자에 표시하고 확인 후 PATCH합니다.

| 권한 | M3 노출 |
|---|---|
| OWNER | 모든 M3 기능 |
| EDIT | 오버라이드·상태 편집, 이력 조회 (M4에서 부여) |
| MANAGE | 기존 Item 재고 추가·삭제·이동 (M4에서 부여) |
| VIEW | 목록·상세·이력 조회 |

- UI는 `effectivePermission`·`isShared`를 사용하며 숨김은 편의일 뿐 서버가 최종 판정합니다. Query 키는 `["inventory","list",filters]`, `["inventory","detail",id]`, `["inventory",id,"movements"]`, `["item","list",filters]`, `["item",id]`, `["category","list"]`로 고정해 M2 `["hierarchy",...]`와 충돌하지 않게 합니다.
- 이동은 상세·목록·이력, 마스터 수정은 Item·목록·모든 Inventory, 카테고리 병합은 Category·Item·Inventory와 M2 `inventoryCount`를 무효화합니다.
- 로딩은 스켈레톤, 빈 목록은 생성 CTA, 오류는 재시도·오류 코드, 403/404는 SCR-018, 409는 재조회 대화상자, 저장 중은 버튼 비활성으로 처리합니다.
- 상태는 색과 텍스트를 함께 쓰고 44px 터치, 키보드 포커스, 포커스 트랩, `aria-live` 토스트를 적용합니다. 시각 규칙은 [디자인 시스템](../design-system.md)을 따릅니다.

## 7. 작업 분해

| 순서 | 작업 | 주요 파일 | 검증 |
|---|---|---|---|
| 1 | 마이그레이션·데이터 사전 델타 | `db/migration/V6__category.sql`~`V9__inventory_movement.sql`, `docs/generated/data-dictionary.md` | `M3MigrationTest`, `flyway validate` |
| 2 | 멱등성 경로·오류 코드 | `common/idempotency/IdempotencyFilter.kt` 설정, `common/error/ErrorCode.kt`, `Problem.meta` | `IdempotencyTest` |
| 3 | Category 영속성·서비스·API | `inventory/{domain,repository,service,api}`, `mapper/inventory/CategoryMapper.xml` | `CategoryMergeTest`, `CategoryApiTest` |
| 4 | Item 영속성·서비스·API | `inventory/{domain,repository,service,api}`, `mapper/inventory/ItemMapper.xml` | `EffectiveFieldTest`, `ItemUpdateImpactTest` |
| 5 | Inventory·M2 계층 연동 | `inventory/{repository,service,api}`, `mapper/inventory/InventoryMapper.xml`, `hierarchy/service/HierarchyDeleteGuard.kt` | `InventoryStatusTest`, `HierarchyInventoryIntegrationTest` |
| 6 | 이동·이력 | `inventory/service/InventoryMoveService.kt`, `inventory/repository/InventoryMovementMapper.kt`, `mapper/inventory/InventoryMovementMapper.xml` | `InventoryMoveTest`, `MovementApiTest` |
| 7 | OpenAPI·계약·타입 | `docs/generated/openapi.yaml`, `docs/contracts.md`, `docs/generated/events.yaml`, `frontend/src/shared/api/schema.d.ts` | `contract_validation.py`, `pnpm generate:api` |
| 8 | 프론트 데이터 계층·목록 | `features/inventory/api/*`, `inventory-list-page.tsx`, `components/inventory-filters.tsx`, `app/router.tsx` | `inventory-list-page.test.tsx` |
| 9 | 상세·Item 상세 | `inventory-detail-page.tsx`, `item-detail-page.tsx`, `components/{inventory-info-table,movement-timeline,item-update-impact-dialog}.tsx` | `inventory-detail-page.test.tsx`, `item-detail-page.test.tsx` |
| 10 | 편집·생성·폐기 | `inventory-{edit,create}-page.tsx`, `components/{override-field,disposal-dialog,version-conflict-dialog,location-picker}.tsx` | `inventory-edit-page.test.tsx`, `inventory-create-page.test.tsx` |
| 11 | 카테고리 관리·E2E | `features/categories/*`, `frontend/e2e/inventory.spec.ts` | `category-settings-page.test.tsx`, Playwright |
| 12 | 문서·CI·관측 마감 | `.github/workflows/ci.yml`, 대시보드 정의, `docs/index.yaml`의 `last_verified_commit`(별도 승인) | `docs_check.py`, 전체 CI |

- 1~2번은 병렬화할 수 있고, 7번은 3~6번의 응답 필드가 확정된 뒤, 8번은 7번 이후에 진행합니다. 12번에서 `docs/index.yaml`은 수정하지 않으며 커밋 담당자가 `last_verified_commit`을 갱신합니다.

## 8. 테스트

| 영역 | 테스트 파일·클래스 | 핵심 케이스 |
|---|---|---|
| 효과 필드 | `EffectiveFieldTest` | `nameOverride_wins`, `nullOverride_fallsBack`, `emptyDescription_isExplicit`, `priceZero_isExplicit`, `effectiveCategory_usesOverride` |
| 전파·영향 | `ItemUpdateImpactTest` | `changedFieldWithoutOverride_isAffected`, `overrideField_isProtected`, `mixedOverrides_countedInBoth`, `disposedIncluded` |
| 해제 | `OverrideReleaseTest` | `patchNull_releasesOverride`, `patchAbsent_keepsOverride`, `masterNull_clearsValue` |
| 상태·폐기 | `InventoryStatusTest` | `disposalRequiresConfirmation`, `confirmWithoutDisposed_422`, `restoreFromDisposed_allowed`, `defaultListExcludesDisposed`, `explicitStatusIncludesDisposed` |
| 위치 CHECK | `InventoryLocationConstraintTest` | `twoLocations_rejected`, `zeroLocations_rejected`, `crossSpaceCompositeFk_rejected` |
| 이동·no-op | `InventoryMoveTest` | `moveUpdatesOneLocationColumn`, `moveWritesHistory`, `sameLocation_noOp_noHistory_noVersionBump`, `staleVersion_409`, `targetOutsideSpace_404` |
| 버전 | `VersionConflictTest` | `stalePatch_409`, `patchIncrementsVersion`, `emptyPatch_noBump` |
| 멱등성 | `IdempotencyTest` | `sameKeySameBody_replays`, `sameKeyDifferentBody_409`, `concurrentSameKey_createsOnce`, `expiredKey_allowsNew`, `keyScopedByUser` |
| 테넌트 격리 | `TenantIsolationTest` | `getOtherSpaceInventory_404`, `patchOtherSpaceInventory_404`, `createUsesSessionSpace` |
| 카테고리 | `CategoryMergeTest`, `CategoryApiTest` | `normalizeNfkcAndCase`, `duplicateNormalizedName_409`, `mergeUpdatesItemAndOverride`, `mergeSelf_422`, `deleteInUse_409` |
| 권한 | `ItemPermissionTest` | `nonOwnerCreateItem_403`, `nonOwnerMasterPatch_403`, `inventoryEditRequiresEdit` |
| M2 연동 | `HierarchyInventoryIntegrationTest` | `roomWithInventory_deleteBlocked`, `shelfWithInventory_deleteBlocked`, `boxWithInventory_deleteBlocked`, `inventoryCountIncludesDisposed` |
| 마이그레이션 | `M3MigrationTest` | `tablesAndConstraintsExist`, `flywayValidate`, `partialIndexesExist`, `idempotencyTableNotRecreated` |
| 계약 | `InventoryApiTest` | `mergePatchNullSemantics`, `problemDetailsCodes`, `csrfRequired` |
| 프론트 | `inventory-list-page.test.tsx`, `inventory-detail-page.test.tsx`, `inventory-edit-page.test.tsx`, `inventory-create-page.test.tsx`, `item-detail-page.test.tsx`, `category-settings-page.test.tsx` | 필터·기본 폐기 제외, 배지·타임라인, `null` 해제·폐기 확인·409, 소유자 전용·키 재사용, 영향 대화상자, 병합·삭제 |
| E2E | `frontend/e2e/inventory.spec.ts` | 카테고리 → Item+재고 → 오버라이드 → 이동 → 폐기 → 필터 |

- 권한 테스트는 M2의 `AccessService`·`GrantLookup` fake를 재사용하고 M4에서 실제 Grant 통합 테스트로 교체합니다.
- 모든 통합 테스트는 Testcontainers PostgreSQL에서 실행하고 교차 Space는 별도 fixture로 검증합니다.
- 커서는 동일 `updated_at`에서 `id` tie-breaker로 중복·누락이 없는지 확인합니다.
- 멱등성 테스트는 M2 `idempotency_record`를 재사용하고 중복 테이블이 생기지 않았는지 확인합니다.

## 9. 계약·문서·CI·관측

- OpenAPI에 5번 항목의 필드, `status` 반복, `update-impact.fields`, Category 병합 응답, M2 계층 `inventoryCount`, `Problem.meta`, 새 오류 코드를 반영합니다.
- 데이터 사전에 M3 인덱스, `item.currency` CHECK, 이동 이력 인덱스·불변성을 반영합니다. `idempotency_record`는 M2 정의를 유지합니다.
- 생성기 전이면 [ADR-014](../decisions/ADR-014-generated-docs-draft.md)에 따라 초안 머리말을 유지해 수동 갱신하고, 준비되었으면 `bash scripts/docs_generate.sh all`만 사용합니다.
- M3는 `inventory.*` 이벤트를 발행하지 않고 `outbox_event`도 만들지 않는 안을 권장합니다. 소비자가 없고 늦은 이벤트가 M6 읽기 모델에 오래된 상태를 줄 수 있습니다. 대안은 M3에 테이블·행만 만들고 M5에서 릴레이를 붙이는 것이나 `PENDING` 적체와 순서 문제를 검증해야 합니다.
- 이벤트 시점은 착수 전 **확인 필요**이며 권장안은 M5 이연입니다. 결정 시 `docs/generated/events.yaml`과 계약의 활성화 시점을 함께 갱신합니다.
- CI 백엔드 job에 `M3MigrationTest`, `InventoryApiTest`, `IdempotencyTest`, `TenantIsolationTest`, `HierarchyInventoryIntegrationTest`를 포함하고 프론트 job에 새 Vitest와 `pnpm build`를 포함합니다. Playwright는 야간 job을 권장하고 M8에서 PR gate로 승격합니다.
- `docs_check.py`, `docs_impact.py`, `scripts/contract_validation.py`를 통과하고 `pnpm generate:api` 결과를 커밋해 타입 차이를 막습니다. 마이그레이션은 번호 충돌과 `flyway validate`를 리뷰합니다.
- 로그에 `request_id`, `space_id`, `user_id`, `item_id`, `inventory_id`, `movement_id`를 포함하고 설명·가격 원문은 남기지 않습니다. 메트릭은 `agolock.inventory.create`, `update`, `move`, `dispose`, `version_conflict`, `idempotency.replay`, `category.merge`와 목록 타이머를 수집하고 409·422·no-op·재생 비율을 대시보드에 표시합니다.
- `audit_log`는 M4 범위입니다. 이동 이력은 화면용이며 감사 로그를 대체하지 않습니다.

## 10. 완료 체크리스트

| M3 완료 조건 | 검증 | 상태 |
|---|---|---|
| M0·M1과 M2(PLAN-003) 인터페이스 동결 | M2 완료 조건, `AccessService`·`SpaceOwnership` 테스트 | 대기 |
| `category`·`item`·`inventory`·`inventory_movement` 마이그레이션, M2 멱등성 재사용 | `M3MigrationTest`, `flyway validate` | 대기 |
| 효과 필드·오버라이드 해제 | `EffectiveFieldTest`, `OverrideReleaseTest` | 대기 |
| 마스터 수정의 무오버라이드 재고 전파 | `ItemUpdateImpactTest`, `InventoryApiTest` | 대기 |
| 소유자 전용 Item 생성·마스터 수정·카테고리 관리 | `ItemPermissionTest`, `CategoryApiTest` | 대기 |
| 공유 사용자 Inventory 흐름은 M4로 이연 | M4 연결 목록, M3 테스트 제외 확인 | 대기 |
| 위치 CHECK·복합 FK | `InventoryLocationConstraintTest` | 대기 |
| 이동 이력·같은 위치 no-op | `InventoryMoveTest`, `MovementApiTest` | 대기 |
| version 충돌·Idempotency-Key 재생 | `VersionConflictTest`, `IdempotencyTest` | 대기 |
| 폐기 확인·기본 목록 제외 | `InventoryStatusTest` | 대기 |
| 카테고리 정규화·병합 | `CategoryMergeTest` | 대기 |
| M2 계층 재고 개수·삭제 차단 | `HierarchyInventoryIntegrationTest` | 대기 |
| SCR-008·009·010·011·012와 카테고리 관리 | Vitest, Playwright | 대기 |
| OpenAPI·데이터 사전 재생성 | `docs_generate.sh`, `pnpm generate:api` | 대기 |
| 문서·린트·테스트·빌드 | `docs_check.py`, `ktlintCheck`, `detekt`, `pnpm lint`, `pnpm test`, `pnpm build` | 대기 |
| 교차 테넌트 차단·관측 | `TenantIsolationTest`, 로그·메트릭 샘플 | 대기 |

## 11. 위험, 미결정, M4 연결

### 11.1 위험

| 위험 | 영향 | 대응 |
|---|---|---|
| M1·M2 인터페이스 변경 | M3 재작업 | `AccessService`·`SpaceOwnership`·마이그레이션 번호 동결 |
| Merge Patch·`version` 의미 혼동 | 잘못된 해제·덮어쓰기 | `version`을 사전 조건으로 고정, API 테스트 |
| 효과 필드 조회 성능 | 목록 p95 저하 | 단일 조인, 부분 인덱스, M6 읽기 모델 검토 |
| M2 멱등성·삭제 가드 누락 | 중복 생성·재고 있는 위치 삭제 | M2 보완, 동시성·`HierarchyInventoryIntegrationTest` |
| 카테고리 병합 잠금 | 교착·긴 트랜잭션 | ID 순서 잠금, 영향 건수 제한 |
| 마스터 수정 후 캐시 불일치 | 오래된 효과 값 | TanStack 무효화, 409 재조회 |
| Outbox 시점·이미지·검색 미구현 | 이벤트 누락, UI 빈 영역 | 9번 결정, 플레이스홀더·`hasImage` 숨김 |

### 11.2 미결정과 권장안

| 항목 | 상태 | 권장안 |
|---|---|---|
| Outbox·이벤트 도입 시점 | **확인 필요** | M5 이연, M3는 이동 이력만 |
| Category `version` | **확인 필요** | M3 미도입 |
| M2 멱등성 동시성·M1 계획 문서 | **확인 필요** | M2 보완, PLAN-002 후 M1 완료 |
| 이름 오버라이드 빈 문자열 | **확인 필요** | 422 거부, 설명만 허용 |
| Category 병합 응답 | **확인 필요** | 200과 건수 |
| `inventoryCount`의 `DISPOSED` | **확인 필요** | 포함, 상세 목록은 필터 |
| `hasImage`·`VIEWED_DESC` | **확인 필요** | 422 또는 M5·M6까지 숨김 |
| 이동 이력 위치 이름 | **확인 필요** | ID 저장, 조회 시 현재 이름 |
| 마스터 수정 서버 확인 | **확인 필요** | 플래그 없이 영향 API만 |

구조 결정은 ADR로 남깁니다. 후보는 "M3 이벤트·Outbox 도입 시점"과 "Inventory 오버라이드 응답·영향 범위 표현"이며, M2가 ADR-017·018 후보를 사용 중이므로 번호는 M2 확정 후 이어 붙입니다.

### 11.3 M4 연결

- M2의 `GrantLookup`을 SQL 구현으로 교체하고 `AccessService` 호출부·위치 노드 `AccessTarget` 계산은 유지합니다.
- `GET /inventories`, `/items`, `/items/{itemId}/inventories`의 `scope`·`effectivePermission`·`isShared`를 실제 공유 범위로 채웁니다.
- 공유 `MANAGE`의 기존 Item Inventory 추가·삭제·이동을 연결하고 신규 Item·마스터·카테고리 관리는 차단하며, `move-targets`는 source·target 모두 `MANAGE`인 위치만 반환합니다.
- `inventory_movement`는 공유 사용자의 이동에도 `moved_by`와 함께 기록합니다. M4 감사 로그 범위와 공유 사용자 테스트의 `InventoryService` 재사용은 **확인 필요**입니다.

M5는 `primaryImageId`·Inventory 이미지, M6은 M3 목록 필터를 검색 조건·읽기 모델로 확장합니다. M6 마이그레이션 번호는 M5 완료 시점에 다시 확정합니다.


## 13. M3 핵심 구현 결과

| 영역 | 결과 |
|---|---|
| 마이그레이션 | `V6__category.sql`, `V7__item.sql`, `V8__inventory.sql`, `V9__inventory_movement.sql` |
| API | Category·Item·Inventory CRUD, 위치 이동, 이동 이력, Item별 Inventory |
| 유효 값 | Item 마스터 + Inventory 오버라이드 조합, PATCH 필드 존재 여부 추적, 변경 없음 시 version 유지 |
| 상태 | DISPOSED 전환 시 `confirmDisposal` 요구, 기본 목록에서 DISPOSED 제외 |
| M2 연동 | Room·Shelf·Box `inventoryCount`, `HierarchyDeleteGuard`의 Inventory 삭제 차단 |
| 확장 API | Category 병합 200건수, Inventory/Item cursor, 반복 status·Item q, Idempotency-Key |
| 화면 | `/inventory`에서 카테고리·Item·Inventory 생성과 목록 확인 |
| 검증 | backend 63, frontend 17, E2E 6, docs 41 |

후속 보완은 목록의 반복 status·category·위치·scope·cursor 필터와 hasImage 422 처리입니다. 공유 사용자의 M3 Inventory 작업은 M4에서 확장합니다.

## 14. M3 검증 보강

- `InventoryCursorTest`: cursor 왕복·invalid cursor·limit 경계
- `InventoryIntegrationTest`: Item/Inventory cursor, 반복 status, Item q, DISPOSED Item 삭제 차단, Category merge 200건수
- `SharingIntegrationTest`: 공유 Item/Inventory 읽기, 공유 MANAGE, Share 철회 감사
- M3 완료 체크리스트 10번은 남은 항목(전체 화면·관측)을 제외하고 실제 테스트 증빙과 함께 갱신 필요

### 10.1 현재 검증 상태 (2026-10-21)

| 항목 | 상태 | 실제 증빙 |
|---|---|---|
| V6~V9 마이그레이션 | 완료 | `DatabaseMigrationTest`, Flyway 적용 |
| 효과 필드·오버라이드 | 완료 | `InventoryIntegrationTest`, merge-patch 테스트 |
| 위치 CHECK·이동·no-op | 완료 | `InventoryIntegrationTest` |
| version·Idempotency-Key | 완료 | 통합 테스트·생성 API |
| 폐기 확인·기본 제외 | 완료 | `InventoryIntegrationTest` |
| Category 병합 | 완료 | `InventoryIntegrationTest` 200건수 |
| 반복 status·Item q·cursor | 완료 | `InventoryCursorTest`, `InventoryIntegrationTest` |
| Item 수정 영향·삭제 차단 | 완료 | DISPOSED 포함 테스트 |
| M2 재고 개수·삭제 차단 | 완료 | `HierarchyIntegrationTest` |
| 전체 화면·관측 | 부분 | 현재 프론트엔드 등록 UX 변경 진행 중, 메트릭 미구현 |
