# PLAN-003: M2 계층과 권한 기반 상세 구현 계획

> 상태: 구현 전 계획 (M1 완료 후 착수)
> 역할: 구현 로드맵 M2의 스키마, AccessService 기반, 계층 CRUD·이동·삭제, 계층 UI의 실행 계획
> 상위 문서: [구현 로드맵](../implementation-roadmap.md)
> 선행 문서: [PLAN-001 기반 부트스트랩](PLAN-001-foundation.md) · [PLAN-001 계약·CI·검증](PLAN-001-foundation-delivery.md)
> 관련 문서: [아키텍처](../architecture.md), [도메인](../domain.md), [공유](../sharing.md), [계약](../contracts.md), [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md), [데이터 사전](../generated/data-dictionary.md), [ADR-001](../decisions/ADR-001-space-tenant.md), [ADR-002](../decisions/ADR-002-inventory-location.md), [ADR-003](../decisions/ADR-003-permission-model.md), [ADR-004](../decisions/ADR-004-mybatis.md), [ADR-010](../decisions/ADR-010-item-creation-permissions.md), [ADR-013](../decisions/ADR-013-invitation-only-sharing.md), [ADR-016](../decisions/ADR-016-neo-brutal-design-system.md)

## 1. 목적과 범위

M2의 목적은 위치 계층을 실제로 사용할 수 있게 만들면서, M4 공유가 재작성 없이 얹힐 수 있는 권한 판정 기반을 고정하는 것입니다. 화면만 보고 권한을 판단하지 않고, 모든 계층 응답과 변경 요청이 `AccessService`를 통과하도록 만듭니다.

| 영역 | M2 산출물 |
|---|---|
| 데이터 | `room`, `shelf`, `box`, 복합 FK, soft delete, 조회·삭제 차단 인덱스, 커서 정렬 |
| 권한 | `Permission` enum, `AccessService`, `effectivePermission`, `isShared`, 소유자 전용 규칙, M4용 `GrantLookup` 포트 |
| API | Room·Shelf·Box CRUD 17개 엔드포인트, 부모 이동 no-op, 삭제 차단, 오류 매핑, 멱등성, 페이지네이션 |
| 화면 | SCR-004 공간 트리, SCR-005 Room 상세, SCR-006 Shelf 상세, SCR-007 Box 상세, 생성·수정·이동·삭제 대화상자 |
| 검증 | 교차 Space, 복합 FK, 권한 상속, 삭제 차단, 이동 no-op, UI 권한 분기, 계약 재생성 |

### 1.1 비목표

- `share`, `share_invitation` 테이블, `GET /shared`, 초대·수락·철회·권한 변경, 비소유자 접근 경로. M2의 `isShared`는 항상 `false`입니다.
- `item`, `inventory`, `category`, 이미지, 검색, 이동 이력, 감사 로그 테이블
- `audit_log`, Outbox, RabbitMQ 이벤트. M2는 구조화 로그만 남깁니다.
- 수동 정렬, 이름 유일 제약, Space 전환, 다중 Space, 계층 복사
- `GET /hierarchy/tree` 같은 신규 트리 엔드포인트와 RLS. M2는 기존 목록 3종의 지연 로딩과 복합 FK·애플리케이션 ACL을 사용합니다.

## 2. 선행 조건

### 2.1 M0에서 확정된 기반

M0의 Gradle·Testcontainers·Flyway·MyBatis, `UuidV7`/`ClockProvider`, problem+json 처리, React Router·TanStack Query·API client·디자인 토큰, OpenAPI 미리보기와 `pnpm generate:api`를 그대로 사용합니다. 마이그레이션과 PostgreSQL 통합 테스트, 화면 라우트, 계약 타입 재생성이 모두 이 기반 위에 추가됩니다.

### 2.2 M1에서 받아야 하는 인터페이스

M1 구현이 진행 중이므로, M2 착수 전에 아래 항목의 완료 여부를 확인합니다. 없으면 M2가 아니라 M1 보완으로 먼저 처리합니다.

| 항목 | 확인할 것 | 없을 때 |
|---|---|---|
| 세션 인증 | `SecurityConfig`가 `/api/v1/**`를 인증 필수로 보호 | M2 착수 중단, M1 보완 |
| 현재 사용자 | `Actor(userId, ownSpaceId)`, argument resolver 또는 `@AuthenticationPrincipal` | M2에서 `com.agolock.tenancy`에 최소 구현 추가 |
| Space 소유자 | `space.owner_user_id` 조회와 `SpaceOwnership` 포트 | M2에서 `tenancy`에 추가 |
| CSRF | 상태 변경 요청의 `X-XSRF-TOKEN` 검증 | M1 보완 |
| 오류 코드 | 401, 403 `PERMISSION_DENIED`, 404 `RESOURCE_NOT_FOUND`, 409 `VERSION_CONFLICT` | M2에서 `ApiException` 하위 코드 추가 |
| 멱등성 | `idempotency_record`와 필터 | M2 `V5__idempotency.sql`에 포함 |
| 계약 기록 모드 | M1이 `docs/generated/` 기록 모드로 전환했는지 | 전환 전이면 [ADR-014](../decisions/ADR-014-generated-docs-draft.md)에 따라 초안을 수동 갱신 |

`Actor`는 요청마다 서버 세션에서 복원하며, 요청 본문·쿼리·헤더의 `spaceId`를 신뢰하지 않습니다. M2 경로에는 `spaceId` 파라미터 자체를 두지 않습니다.

## 3. 데이터 모델과 마이그레이션

컬럼, 타입, 제약의 단일 원본은 [데이터 사전 4번 항목](../generated/data-dictionary.md)입니다. 이 계획은 M2에서 추가로 확정하는 인덱스, 정렬, 삭제, 잠금 규칙만 정의하고 DDL을 재정의하지 않습니다.

### 3.1 테이블과 제약

| 테이블 | 핵심 컬럼 | 제약 | M2 인덱스 |
|---|---|---|---|
| `room` | `id`, `space_id`, `name`, `description`, `version`, `created_at`, `updated_at`, `deleted_at` | `UNIQUE (id, space_id)`, FK `space` | `(space_id, name, id) WHERE deleted_at IS NULL` |
| `shelf` | `room` 컬럼 + `room_id` | `UNIQUE (id, space_id)`, `FOREIGN KEY (room_id, space_id) REFERENCES room(id, space_id)` | `(space_id, room_id, name, id) WHERE deleted_at IS NULL` |
| `box` | `room` 컬럼 + `shelf_id` | `UNIQUE (id, space_id)`, `FOREIGN KEY (shelf_id, space_id) REFERENCES shelf(id, space_id)` | `(space_id, shelf_id, name, id) WHERE deleted_at IS NULL` |
| `idempotency_record` | 데이터 사전 9번 항목 | M1에 없으면 M2에서 생성 | `(user_id, key)` |

- 부모 FK는 `ON DELETE RESTRICT`를 기본값으로 두고 cascade를 사용하지 않습니다. 복합 FK는 부모의 `(id, space_id)` 유니크 제약을 참조하며, 자식의 `space_id`를 바꾸는 API는 만들지 않습니다.
- 마이그레이션 파일은 `backend/src/main/resources/db/migration/V4__hierarchy.sql`, 멱등성 테이블이 없을 때만 `V5__idempotency.sql`을 추가합니다. 현재 M1이 V2, V3을 사용하므로 다음 번호는 V4입니다.
- 인덱스는 `space_id`를 선두에 두고 soft delete 필터를 부분 인덱스 조건에 포함하며, 정렬 키 `(name, id)`를 포함해 커서 조회가 인덱스만 사용하게 합니다. `inventory` 위치 인덱스는 M3에서 추가합니다.

### 3.2 `space_id` 규칙

- `room.space_id`는 `Actor.ownSpaceId`에서만 설정하고, `shelf.space_id`는 부모 Room, `box.space_id`는 부모 Shelf의 값을 복사합니다. 부모 이동은 `room_id` 또는 `shelf_id`만 바꾸며 `space_id`는 바꾸지 않습니다.
- 모든 상세 조회는 ID로 행을 읽은 뒤 `AccessService`가 Space 소유자와 Grant를 판정합니다. 목록 조회는 `space_id = :actorSpaceId`를 SQL에 명시하고, 교차 Space 참조는 복합 FK가 1차로 막은 뒤 서비스가 404로 변환합니다.

### 3.3 Soft delete

- 삭제는 `deleted_at = now()`, `updated_at = now()`, `version = version + 1` 갱신입니다.
- 모든 조회와 자식 존재 검사는 `deleted_at IS NULL`을 포함합니다.
- soft delete된 부모는 FK상 여전히 참조 가능하므로, 생성·이동 시 부모를 `FOR UPDATE`로 잠그고 `deleted_at IS NULL`을 다시 확인합니다. FK만으로 부모의 생존을 보장하지 않습니다.
- 삭제된 이름은 재사용할 수 있습니다. 복구 API는 M2에 없고 운영 복구는 DB 관리자 절차로만 수행합니다.

### 3.4 정렬, 유일성, 커서

- 목록과 트리는 `name ASC, id ASC`로 고정합니다. 대소문자·공백 정규화는 저장 시 앞뒤 공백 제거만 수행합니다.
- `(name, id)` 복합 비교로 커서를 구현하고, 커서는 `v`, `name`, `id`를 담은 base64url 문자열입니다. 잘못된 커서는 `400 INVALID_CURSOR`입니다.
- 같은 부모 아래 이름 중복은 허용합니다. 제품 사양에 유일성 요구가 없고, 중복을 막으면 기존 데이터 마이그레이션이 어려워집니다. UI는 경로로 구분합니다. (권장, 11.2번 항목에서 확인)
- 수동 정렬(`sort_order`)은 M2에 넣지 않습니다. 필요하면 별도 마이그레이션과 ADR로 추가합니다.

### 3.5 잠금 순서

동시 생성·이동·삭제는 Room → Shelf → Box 순서로만 잠급니다.

- 삭제: 부모를 잠그고 Room은 `shelf`, Shelf는 `box` 존재를 세며, M3부터 `inventory` 존재도 함께 검사한 뒤 soft delete합니다.
- 생성·이동: 부모를 먼저 잠그고 생존·같은 Space를 확인한 뒤 자식을 insert·update합니다. 대상 부모가 삭제되었으면 404입니다.
- 이 순서를 벗어나는 잠금을 추가하지 않습니다.

## 4. 도메인과 서비스 설계

### 4.1 패키지 구조

| 패키지 | 파일 | 책임 |
|---|---|---|
| `com.agolock.hierarchy.api` | `RoomController.kt`, `ShelfController.kt`, `BoxController.kt`, `HierarchyDtos.kt` | HTTP, 검증, 201/204, `Location` |
| `com.agolock.hierarchy.domain` | `Room.kt`, `Shelf.kt`, `Box.kt`, `NodeType.kt` | 불변식, 이름 검증, 버전 |
| `com.agolock.hierarchy.repository` | `RoomMapper.kt`, `ShelfMapper.kt`, `BoxMapper.kt`, `HierarchyQueryMapper.kt` | MyBatis 인터페이스 |
| `com.agolock.hierarchy.service` | `RoomService.kt`, `ShelfService.kt`, `BoxService.kt`, `HierarchyDeleteGuard.kt`, `HierarchyCursor.kt` | 트랜잭션, 이동·삭제 규칙, 커서 |
| `com.agolock.sharing.permission` | `Permission.kt` | 권한 enum과 순서 |
| `com.agolock.sharing.access` | `AccessService.kt`, `AccessTarget.kt`, `EffectivePermission.kt`, `GrantLookup.kt`, `NoGrantLookup.kt` | 유효 권한 판정, M4 확장 포트 |
| `com.agolock.tenancy` | `Actor.kt`, `ActorContext.kt`, `SpaceOwnership.kt` | M1에서 제공, 없으면 M2에서 최소 구현 |
| `com.agolock.common.idempotency` | `IdempotencyFilter.kt`, `IdempotencyService.kt` | POST 재생, 키 재사용 충돌 |

MyBatis XML은 `backend/src/main/resources/mapper/hierarchy/RoomMapper.xml`, `ShelfMapper.xml`, `BoxMapper.xml`에 두고, `application.yml`의 `mybatis.mapper-locations`에 `classpath:mapper/**/*.xml`을 추가합니다.

### 4.2 권한 enum과 순서

```kotlin
enum class Permission(val rank: Int) {
    NONE(0), VIEW(1), EDIT(2), MANAGE(3), OWNER(4);
}
```

- DB `permission` enum은 `VIEW`, `EDIT`, `MANAGE`만 유지합니다. `OWNER`는 저장하지 않고 Space 소유자에게 계산으로 부여하며, API 응답의 `Permission`은 `OWNER`를 포함해야 합니다. 현재 OpenAPI enum에 `OWNER`가 없는 것은 M2에서 반드시 고칠 계약 공백입니다.
- 비교는 `rank`로만 수행하고 문자열 비교를 금지합니다.
- Grant가 여러 개면 최대 rank를 선택합니다. 거부 규칙은 없습니다.

### 4.3 AccessService API

```kotlin
data class AccessTarget(
    val spaceId: UUID,
    val ownerUserId: UUID,
    val nodeType: NodeType,   // ROOM, SHELF, BOX
    val roomId: UUID?,        // ROOM이면 자기 ID, SHELF·BOX이면 조상 Room
    val shelfId: UUID?,       // BOX이면 부모 Shelf
)

data class EffectivePermission(
    val permission: Permission,
    val isShared: Boolean,
)

interface AccessService {
    fun resolve(actor: Actor, target: AccessTarget): EffectivePermission
    fun resolveAll(actor: Actor, targets: List<AccessTarget>): List<EffectivePermission>
    fun require(actor: Actor, target: AccessTarget, minimum: Permission): EffectivePermission
    fun requireOwner(actor: Actor, target: AccessTarget): EffectivePermission
    fun isShared(actor: Actor, target: AccessTarget): Boolean
}

interface GrantLookup {
    fun grantsFor(userId: UUID, roomIds: Set<UUID>, shelfIds: Set<UUID>): List<GrantRef>
}
```

- `NoGrantLookup`은 M2에서 빈 목록을 반환합니다. M4에서 SQL 구현으로 교체하며 호출부는 바꾸지 않습니다.
- `resolve`: 소유자면 `OWNER, isShared=false`; 아니면 `GrantLookup` 결과의 최대 rank, 없으면 `NONE`. `isShared`는 "현재 사용자에게 Grant로 열린 리소스"를 뜻하며, 소유자가 다른 사람에게 공유한 상태는 M4에서 별도 필드가 필요하면 추가합니다.
- `require`: `NONE`이면 404, rank가 부족하면 403 `PERMISSION_DENIED`.
- `resolveAll`은 목록·트리에서 Grant 조회를 한 번으로 묶습니다. M2는 소유자만 있으므로 추가 쿼리가 없고, M4에서도 대상별 N+1을 만들지 않습니다.
- 판정 결과는 캐시하지 않습니다. Grant 철회 후 즉시 차단 원칙을 지키기 위해서입니다.

### 4.4 조회와 판정 흐름

- Tenant context는 M1 세션에서 복원한 `ActorContext(userId, ownSpaceId)`로 고정하고, 리소스 Space는 DB 행에서만 읽습니다. 컨트롤러는 경로 ID로 행을 읽고(`deleted_at IS NULL`), 조회 SQL이 `space_id`, `owner_user_id`, 조상 `room_id`·`shelf_id`를 함께 반환해 `AccessTarget`을 구성합니다.
- `AccessService.require`로 최소 권한을 확인합니다. 없거나 다른 Space면 404이고, 변경은 같은 트랜잭션에서 부모 잠금, 규칙 검사, 버전 증가, 응답 매핑 순서로 진행합니다.
- 목록은 `space_id = :actorSpaceId`로 시작하고 M4에서 `OR EXISTS (share ...)`를 추가합니다. 상세는 ID 조회 후 ACL 판정이므로 M4에서도 경로가 바뀌지 않습니다.
- `AccessTarget` 구성은 `shelf`의 `(id)` PK, `box`의 `(id)` PK와 Room·Shelf PK 조인으로 끝나므로 추가 인덱스가 필요 없습니다.

### 4.5 소유자 전용 규칙

| 작업 | 필요 권한 | M2 동작 |
|---|---|---|
| 목록·상세 조회 | `VIEW` | 소유자는 `OWNER`, 그 외 404 |
| Room·Shelf·Box 생성 | `OWNER` | 부모 Space 소유자만 |
| 이름·설명 수정 | `OWNER` | `version` 필수 |
| Shelf·Box 부모 이동 | `OWNER` | 같은 Space의 대상만 |
| 삭제 | `OWNER` | 자식·재고가 없을 때만 |
| 공유 관리·초대 | `OWNER` | M4 |

M2에는 "보이지만 권한이 부족한" 사용자가 없으므로 실제 403은 M4에서 발생합니다. 그래도 `require`의 403 경로와 단위 테스트는 M2에서 만듭니다.

### 4.6 이동, no-op, 삭제 의미

| 경우 | 결과 |
|---|---|
| Shelf·Box를 같은 부모로 이동 | `200`, `version` 유지, 로그·감사 없음 |
| Shelf·Box를 다른 부모로 이동 | 해당 FK 갱신, `version + 1` |
| 대상 부모 없음·삭제됨·다른 Space | `404 RESOURCE_NOT_FOUND` |
| `version` 불일치 | `409 VERSION_CONFLICT` |
| 하위 Shelf·Box 존재 삭제 | `409 LOCATION_NOT_EMPTY`와 차단 개수 |
| 이미 삭제된 리소스 재삭제 | `404 RESOURCE_NOT_FOUND` |
| 빈 Box 삭제 | `204 No Content` |
| 이름 공백만 입력 | `400 VALIDATION_FAILED` |

- No-op은 버전 검사를 먼저 통과해야 합니다. 버전이 다르면 no-op이라도 409입니다.
- No-op은 응답만 반환하고 `updated_at`, `version`, 로그를 남기지 않습니다.
- 삭제 차단 응답에는 `meta.shelves`, `meta.boxes`, `meta.inventories`를 담아 UI가 이동 안내를 표시합니다. M2의 `inventories`는 항상 0입니다.
- M3에서 `inventory` 테이블이 생기면 `HierarchyDeleteGuard`에 재고 존재 검사를 추가하기 전까지 재고가 있는 위치를 삭제할 수 있습니다. M3 착수 체크리스트에 이 항목을 고정합니다.

## 5. API 계약

Base path는 `/api/v1`이고 인증은 세션 쿠키, 상태 변경은 CSRF를 요구합니다. 상세 규칙은 [계약](../contracts.md)과 [OpenAPI 계약](../generated/openapi.yaml)을 따릅니다.

### 5.1 M2 엔드포인트

| Method | Path | 요청 | 응답 | 권한 |
|---|---|---|---|---|
| GET | `/rooms` | `cursor`, `limit` | `RoomList` | 인증 |
| POST | `/rooms` | `CreateRoomRequest`, `Idempotency-Key` | `201 Room` + `Location` | OWNER |
| GET | `/rooms/{roomId}` | - | `Room` | VIEW |
| PATCH | `/rooms/{roomId}` | `UpdateRoomRequest` | `Room` | OWNER |
| DELETE | `/rooms/{roomId}` | - | `204` | OWNER |
| GET | `/rooms/{roomId}/shelves` | `cursor`, `limit` | `ShelfList` | VIEW |
| POST | `/rooms/{roomId}/shelves` | `CreateShelfRequest`, `Idempotency-Key` | `201 Shelf` + `Location` | OWNER |
| GET | `/shelves/{shelfId}` | - | `Shelf` | VIEW |
| PATCH | `/shelves/{shelfId}` | `UpdateShelfRequest` | `Shelf` | OWNER |
| PATCH | `/shelves/{shelfId}/parent` | `MoveShelfRequest` | `Shelf` | OWNER |
| DELETE | `/shelves/{shelfId}` | - | `204` | OWNER |
| GET | `/shelves/{shelfId}/boxes` | `cursor`, `limit` | `BoxList` | VIEW |
| POST | `/shelves/{shelfId}/boxes` | `CreateBoxRequest`, `Idempotency-Key` | `201 Box` + `Location` | OWNER |
| GET | `/boxes/{boxId}` | - | `Box` | VIEW |
| PATCH | `/boxes/{boxId}` | `UpdateBoxRequest` | `Box` | OWNER |
| PATCH | `/boxes/{boxId}/parent` | `MoveBoxRequest` | `Box` | OWNER |
| DELETE | `/boxes/{boxId}` | - | `204` | OWNER |

`GET /shared`는 M2에서 구현하지 않습니다. 계약에는 이미 있으므로 M4에서 초대·Grant와 함께 채웁니다.

### 5.2 응답 필드

응답 예: `{"id":"018f...","name":"거실","description":null,"shelfCount":2,"version":3,"effectivePermission":"OWNER","isShared":false}`

- `Room`에 `shelfCount`, `Shelf`에 `boxCount`를 추가합니다. 접힌 트리 노드의 개수를 자식 조회 없이 표시하기 위해서입니다. `Permission` enum에는 `OWNER`를 추가하며 DB enum과 API enum은 다릅니다.
- `Shelf`의 `roomId`, `Box`의 `shelfId`는 유지합니다. 브레드크럼은 부모 상세를 캐시에서 재사용하거나 1~2회 조회해 구성합니다.
- `ShelfList`, `BoxList`에 `nextCursor`를 추가하고 두 목록에 `cursor`, `limit`을 허용합니다. `Problem`에는 선택 필드 `meta`를 추가해 삭제 차단 개수를 담고 `fieldErrors`를 재사용하지 않습니다.

### 5.3 이동 요청

```json
{ "roomId": "018f...", "version": 3 }
```

- `PATCH /shelves/{shelfId}/parent`는 `roomId`, `PATCH /boxes/{boxId}/parent`는 `shelfId`를 받습니다.
- 같은 부모면 200 no-op입니다. 응답 `version`은 요청 전과 같습니다.
- 다른 Space의 부모는 404입니다. 403이 아닙니다. 존재 여부를 숨기기 위해서입니다.

### 5.4 오류와 버전

| 상황 | 상태 | 코드 |
|---|---|---|
| 이름 누락·길이 초과, 잘못된 본문 | 400 | `VALIDATION_FAILED` |
| 잘못된 커서 | 400 | `INVALID_CURSOR` |
| 세션 없음 | 401 | M1 코드 |
| 다른 Space·삭제됨·없는 ID | 404 | `RESOURCE_NOT_FOUND` |
| 보이지만 권한 부족 (M4) | 403 | `PERMISSION_DENIED` |
| `version` 불일치 | 409 | `VERSION_CONFLICT` |
| 하위 리소스·재고 존재 | 409 | `LOCATION_NOT_EMPTY` |
| 같은 키로 다른 요청 | 409 | `IDEMPOTENCY_KEY_REUSED` |

- PATCH와 부모 이동은 `version`을 필수로 받습니다. DELETE는 계약상 본문이 없으므로 `version`을 받지 않고, 부모 잠금과 재검사로 동시성을 처리합니다. (권장, 11.2번 항목에서 확인)
- 삭제 성공은 `204`, 생성 성공은 `201`과 `Location: /api/v1/rooms/{id}` 형식입니다.
- PATCH는 JSON Merge Patch입니다. `description` 생략은 유지, `null`은 설명 삭제입니다.

### 5.5 멱등성과 페이지네이션

- POST 3종은 `Idempotency-Key`를 지원합니다. 키는 사용자별 24시간, 요청 해시는 method·path·본문으로 계산하며 같은 키와 같은 요청은 이전 `201` 응답과 `Location`을 재생하고 다른 본문이면 409입니다. 4xx 응답은 저장하지 않습니다.
- `limit` 기본 20, 최대 100이며 `nextCursor`가 null이면 마지막입니다.
- 트리는 `GET /rooms` → Room 확장 시 `GET /rooms/{id}/shelves` → Shelf 확장 시 `GET /shelves/{id}/boxes`로 3단계 지연 로딩합니다. 신규 트리 엔드포인트 없이 계약과 페이지네이션을 재사용합니다. (권장, 11.2번 항목)
- OpenAPI `info.version`은 `0.3.0`으로 올리고 기존 필드 제거 없이 추가만 합니다.

## 6. 프론트엔드

### 6.1 라우트와 화면

| 화면 | 라우트 | 파일 |
|---|---|---|
| SCR-004 공간 트리 | `/space` | `frontend/src/features/hierarchy/space-tree-page.tsx` |
| SCR-005 Room 상세 | `/space/rooms/:roomId` | `frontend/src/features/hierarchy/room-detail-page.tsx` |
| SCR-006 Shelf 상세 | `/space/shelves/:shelfId` | `frontend/src/features/hierarchy/shelf-detail-page.tsx` |
| SCR-007 Box 상세 | `/space/boxes/:boxId` | `frontend/src/features/hierarchy/box-detail-page.tsx` |

- `frontend/src/app/router.tsx`의 `space` placeholder를 트리 페이지로 교체하고 상세 라우트를 추가합니다. 데스크톱은 `/space`에서 트리와 선택 노드 상세를 2열로 보여주고 상세 라우트는 딥링크용으로 유지하며, 모바일은 트리에서 상세로 이동합니다.
- 파일: `components/hierarchy-tree.tsx`, `hierarchy-node.tsx`, `breadcrumb.tsx`, `permission-badge.tsx`, `create-*-dialog.tsx`, `edit-node-dialog.tsx`, `move-node-dialog.tsx`, `delete-node-dialog.tsx`, `api.ts`, `queries.ts`.
- 대화상자는 `@radix-ui/react-dialog`와 `@radix-ui/react-alert-dialog`를 고정 버전으로 추가해 포커스 트랩과 ESC 닫기를 확보합니다. 기존 토큰과 `Button`, `Badge`, `Card`를 사용하고 새 색·간격을 만들지 않습니다.

### 6.2 트리 동작

- `role="tree"`, 각 노드 `role="treeitem"`, `aria-expanded`, `aria-level`, `aria-selected`를 사용합니다. 노드는 접힌 상태로 시작하고 확장 시에만 자식 목록을 조회하며 로딩 중에는 스켈레톤 행을 표시합니다.
- 키보드: 위·아래 이동, 오른쪽 확장, 왼쪽 접기, Enter 상세 이동. 포커스는 노드 단위로 유지합니다.
- 노드에는 이름, Room의 `shelfCount`와 Shelf의 `boxCount`, 권한 배지, `isShared`일 때 소유자 배지를 표시합니다. Room은 흰색, Shelf는 노랑 계열, Box는 파랑 계열, 공유 노드는 파랑 배경이라는 [디자인 시스템 7.2번 항목](../design-system.md)을 따릅니다.
- 선택 노드는 primary 배경, 긴 경로는 축약하고 툴팁으로 전체 경로를 제공합니다.
- 빈 상태: Room이 없으면 "아직 방이 없어요"와 "방 만들기"를 표시하고, Shelf·Box 목록이 비면 각각 생성 CTA를 표시합니다. 오류는 인라인 재시도와 `problem.code`를 표시하고 404는 SCR-018 안내로 전환합니다.
- 모바일 390px에서는 한 줄 아코디언, 데스크톱 1280px에서는 왼쪽 트리와 오른쪽 상세로 배치합니다.

### 6.3 상세와 대화상자

| 요소 | Room | Shelf | Box |
|---|---|---|---|
| 헤더 | 이름, 설명, 권한 배지 | 이름, Room 브레드크럼 | 이름, Room › Shelf 브레드크럼 |
| 하위 | Shelf 목록·개수 | Box 그리드·개수 | 직접 위치 재고 빈 상태 (M3) |
| 액션 | Shelf 추가, 수정, 삭제 | Box 추가, 수정, 이동, 삭제 | 수정, 이동, 삭제 |
| 이동 | 없음 | Room 선택 | Shelf 선택 |

- 생성·수정 폼은 React Hook Form과 Zod로 이름 1~120자, 설명 선택 입력을 검증하고 `version`을 hidden 상태로 포함합니다. 409 `VERSION_CONFLICT`면 최신 데이터를 재조회하고 "다른 곳에서 수정되었습니다" 토스트를 표시합니다.
- 이동 대화상자는 같은 Space의 대상만 보여주며 현재 부모를 선택하면 확인 버튼을 비활성화하거나 닫기만 하고 요청을 보내지 않습니다.
- 삭제 대화상자는 `AlertDialog`를 사용합니다. `LOCATION_NOT_EMPTY`면 `meta`의 개수와 "하위 항목을 이동한 뒤 삭제" 안내를 표시합니다.
- 계층 변경 버튼은 `effectivePermission === "OWNER"`일 때만 표시합니다. UI 숨김은 편의이며 서버가 최종 판정합니다. 성공·실패 토스트, 중복 제출 방지, 삭제 후 목록 이동을 적용합니다.

### 6.4 상태와 캐시

| 상태 | 처리 |
|---|---|
| 로딩 | 트리·상세 스켈레톤, 대화상자 제출 중 버튼 비활성 |
| 빈 목록 | 생성 CTA가 있는 빈 상태 |
| 403 | 읽기 전용 배너 (M4에서 실제 발생) |
| 404 | SCR-018 안내와 공간 트리로 돌아가기 |
| 409 버전 충돌 | 재조회 후 토스트, 입력 유지 |
| 409 삭제 차단 | 차단 개수와 이동 안내 |
| 네트워크 오류 | 재시도 버튼, 온라인 복구 시 재조회 |

- Query key는 `["hierarchy","rooms"]`, `["hierarchy","room",id]`, `["hierarchy","room",id,"shelves"]`, `["hierarchy","shelf",id]`, `["hierarchy","shelf",id,"boxes"]`, `["hierarchy","box",id]`로 고정합니다.
- 생성은 부모 목록과 트리, 수정·이동은 해당 노드와 이전·새 부모 목록, 삭제는 부모 목록과 트리를 무효화합니다.
- 낙관적 갱신은 사용하지 않습니다. 서버 응답의 `version`을 신뢰합니다.

## 7. 작업 분해

| 순서 | 작업 | 주요 파일 | 검증 |
|---|---|---|---|
| 1 | M1 인터페이스 확인, `Actor`·`SpaceOwnership`·`idempotency_record` 유무 점검 | `com.agolock.tenancy`, `common/idempotency` | 컴파일, 인증 통합 테스트 1개 |
| 2 | `V4__hierarchy.sql`, 필요 시 `V5__idempotency.sql`, 데이터 사전 초안 갱신 | `db/migration`, `docs/generated/data-dictionary.md` | `DatabaseMigrationTest`, `docs_check.py` |
| 3 | 도메인·MyBatis 매퍼·커서 | `hierarchy/domain`, `resources/mapper/hierarchy` | `HierarchyCompositeFkTest`, `HierarchyPaginationTest` |
| 4 | `Permission`, `AccessService`, `GrantLookup` | `sharing/permission`, `sharing/access` | `AccessServiceTest`, `PermissionOrderingTest` |
| 5 | Room·Shelf·Box CRUD와 오류 매핑 | `hierarchy/api`, `hierarchy/service` | `HierarchyCrudTest`, `HierarchyTenantIsolationTest` |
| 6 | 부모 이동, no-op, 삭제 차단, 잠금 | `HierarchyDeleteGuard`, `*Service` | `HierarchyMoveTest`, `HierarchyDeleteBlockingTest`, `HierarchyConcurrencyTest` |
| 7 | 멱등성 필터와 페이지네이션 | `common/idempotency`, `HierarchyCursor` | `HierarchyIdempotencyTest` |
| 8 | OpenAPI·데이터 사전 갱신, API 타입 재생성 | `docs/generated`, `frontend/src/shared/api/schema.d.ts` | `pnpm generate:api`, diff 없음 |
| 9 | 트리·상세·대화상자 | `frontend/src/features/hierarchy`, `app/router.tsx` | `pnpm lint`, `pnpm test`, `pnpm build` |
| 10 | E2E, 문서·관측 마무리 | `frontend/e2e/hierarchy.spec.ts`, `docs/index.yaml` | `pnpm test:e2e`, `python3 scripts/docs_check.py` |

각 단계는 이전 단계의 테스트가 통과한 뒤 진행합니다. 4번과 5번은 `AccessService` 인터페이스를 먼저 고정하고 컨트롤러를 붙입니다.

## 8. 테스트

백엔드 테스트는 `backend/src/test/kotlin/com/agolock/hierarchy/`를 기본으로 하고, 권한·계약 테스트만 해당 패키지에 둡니다. 프론트 테스트는 `frontend/src/features/hierarchy/`에 둡니다.

| 영역 | 파일 | 핵심 케이스 |
|---|---|---|
| 교차 테넌트 | `backend/src/test/kotlin/com/agolock/hierarchy/HierarchyTenantIsolationTest.kt` | 다른 Space Room 조회·수정·삭제·이동 404, 목록 미노출, 자식 생성 차단 |
| 복합 FK | `HierarchyCompositeFkTest.kt` | 같은 Space Shelf/Box 생성 성공, 다른 Space 부모 insert 실패, `space_id` 불일치 실패 |
| CRUD | `HierarchyCrudTest.kt` | 201·Location, PATCH merge, 설명 null, 이름 검증, 404, 409 버전 |
| 권한 | `backend/src/test/kotlin/com/agolock/sharing/access/AccessServiceTest.kt`, `PermissionOrderingTest.kt` | 소유자 OWNER, 비소유자 NONE, VIEW < EDIT < MANAGE, 상속, 최대 권한, `isShared`, 403/404 매핑, rank 비교 |
| 삭제 차단 | `HierarchyDeleteBlockingTest.kt` | Room에 Shelf, Shelf에 Box, 빈 Box 204, soft delete 자식은 차단 제외, `meta` 개수 |
| 이동 | `HierarchyMoveTest.kt` | 같은 부모 200 no-op·버전 유지, 다른 부모 버전 증가, 다른 Space 404, 삭제 부모 404 |
| 페이지·멱등성 | `HierarchyPaginationTest.kt`, `HierarchyIdempotencyTest.kt` | 이름·ID 정렬, limit 경계, `nextCursor`, `INVALID_CURSOR`, 키 재생, 다른 본문 409, 4xx 미저장 |
| 동시성 | `HierarchyConcurrencyTest.kt` | 부모 삭제와 자식 생성 경합, 이동과 삭제 경합, deadlock 없음 |
| 계약 | `backend/src/test/kotlin/com/agolock/contract/HierarchyContractTest.kt` | operationId, 경로 파라미터, `OWNER` enum, `meta` |
| 프론트 트리 | `frontend/src/features/hierarchy/space-tree-page.test.tsx` | 확장 지연 로딩, 개수, 권한 배지, 빈·로딩·오류, 키보드 |
| 프론트 상세·대화상자 | `room-detail-page.test.tsx`, `shelf-detail-page.test.tsx`, `box-detail-page.test.tsx`, `move-node-dialog.test.tsx`, `delete-node-dialog.test.tsx` | 생성·수정·삭제 차단·브레드크럼, 같은 부모 no-op, 409 재조회 |
| E2E | `frontend/e2e/hierarchy.spec.ts` | 방→선반→박스 생성, 이동, 삭제 차단, soft delete 후 목록 제외 |

- 백엔드 테스트는 Testcontainers PostgreSQL에서 실제 SQL로 검증하고, 각 테스트는 독립 Space를 만듭니다.
- 권한 상속 테스트는 `GrantLookup` fake로 Room VIEW + Shelf EDIT 조합을 만들어 M4 동작을 미리 고정합니다. 실제 Grant 테이블은 만들지 않습니다.
- E2E는 M1 로그인 흐름을 재사용하고, M1이 없으면 API 수준 통합 테스트로 대체합니다.
- 프론트 테스트는 `features/hierarchy/api.ts`를 모킹하고 QueryClient 래퍼를 사용합니다.

## 9. 계약·문서·CI·관측

| 대상 | 변경 |
|---|---|
| OpenAPI | `Permission`에 `OWNER`, `Room.shelfCount`, `Shelf.boxCount`, `ShelfList`·`BoxList`의 `nextCursor`, 목록 `cursor`/`limit`, POST 3종 `Idempotency-Key`, `Problem.meta`, `info.version=0.3.0` |
| 데이터 사전 | `room`, `shelf`, `box` 인덱스와 `idempotency_record` 반영. 컬럼 추가는 없음 |
| 계약 문서 | [계약](../contracts.md)의 엔드포인트 목록은 이미 M2를 포함하므로 새 경로를 추가하지 않음. 삭제 차단 `meta`와 `OWNER` 표현만 계약에 반영 |
| 프론트 타입 | `pnpm generate:api` 후 `frontend/src/shared/api/schema.d.ts`를 함께 커밋 |
| CI | 기존 `docs`, `backend`, `frontend`, `contract` job 유지. backend는 Testcontainers, frontend는 타입 diff·Vitest·Playwright 실행 |
| 관측 | 구조화 로그에 `space_id`, `user_id`, `resource_type`, `resource_id`, `operation`, `result`, `duration_ms` 추가. 이름·설명 원문은 남기지 않음. 메트릭은 계층 API 지연, 403·404 비율, 삭제 차단 수, 이동 수 |

- M2 완료 시 `docs/index.yaml`에 PLAN-003을 등록하고 `last_verified_commit`을 갱신합니다. 이 계획 문서 작성 단계에서는 인덱스를 수정하지 않습니다.
- `python3 scripts/docs_check.py`와 `python3 scripts/docs_impact.py`를 통과해야 합니다.
- [도메인](../domain.md), [공유](../sharing.md), [화면과 흐름](../frontend.md)의 규칙은 변경하지 않습니다. 계약 공백만 OpenAPI에 반영합니다.

## 10. 완료 체크리스트

| M2 로드맵 완료 조건 | 산출물 | 검증 |
|---|---|---|
| 복합 FK로 교차 Space 참조 차단 | `V4__hierarchy.sql`, `HierarchyCompositeFkTest` | 다른 Space 부모 insert 실패 |
| Shelf/Box 부모 이동은 소유자만 | `AccessService`, `ShelfService`, `BoxService` | 비소유자 404, M4 fake 403 |
| 삭제 차단과 soft delete 제외 | `HierarchyDeleteGuard` | `HierarchyDeleteBlockingTest`, 목록 제외 |
| 계층 응답 권한 필드와 UI 일치 | `effectivePermission`, `isShared`, `PermissionBadge` | 계약 테스트·UI 테스트 |
| OpenAPI, 데이터 사전 재생성 | generated 2종 | `docs_generate.sh`, `docs_check.py` |
| 계층 트리와 상세 화면 | SCR-004~007 | Vitest, Playwright |
| API 계약 준수 | 17개 엔드포인트 | `HierarchyContractTest` |
| 품질 gate | ktlint·detekt·ESLint·Prettier | `./gradlew check`, `pnpm lint`, `pnpm build` |

추가 종료 조건:

- `GET /rooms`, `/rooms/{id}/shelves`, `/shelves/{id}/boxes`가 커서와 정렬을 지키고, 같은 부모 이동이 버전을 올리지 않으며, 삭제된 리소스가 모든 목록·상세·자식 검사에서 제외됩니다.
- M4가 `GrantLookup` 구현만 추가하면 되는 상태입니다.

## 11. 위험과 미결정

### 11.1 위험

| 위험 | 영향 | 대응 |
|---|---|---|
| M1 미완료·인터페이스 불일치 | M2 착수 지연 | 2번 항목의 인터페이스 표를 착수 전 확인 |
| OpenAPI에 `OWNER` 부재 | UI가 소유자 버튼을 판단 못 함 | M2 첫 계약 변경으로 추가, ADR-017 |
| 교차 Space를 403으로 응답 | 존재 여부 노출 | 다른 Space는 항상 404, 테스트 고정 |
| 부모 삭제와 자식 생성 경합 | 고아 자식 | 부모 `FOR UPDATE`, 삭제·생성 같은 잠금 순서 |
| FK가 soft delete 부모를 허용 | 삭제된 방 아래 선반 생성 | 잠금 안에서 `deleted_at IS NULL` 재검사 |
| M3 재고 삭제 차단 누락 | 재고 있는 위치 삭제 | M3 착수 체크리스트에 `HierarchyDeleteGuard` 확장 명시 |
| 트리 지연 로딩의 요청 증가 | 체감 지연 | 레벨별 캐시, 개수 필드, 필요 시 M6에서 트리 엔드포인트 검토 |
| 이름 중복 | 사용자 혼동 | 경로 표시, 유일 제약은 확인 후 결정 |
| 멱등성 테이블 소유 불명확 | 중복 생성 | M1에 없으면 M2 마이그레이션에 포함 |

### 11.2 확인 필요와 권장안

| 항목 | 권장안 | 상태 |
|---|---|---|
| API `Permission`에 `OWNER` 포함 | 포함, DB enum은 그대로 | 확인 필요 (권장) |
| `isShared` 의미 | Grant로 접근 중인 리소스, M2는 항상 false | 확인 필요 (권장) |
| 삭제 요청의 `version` | 받지 않고 잠금·재검사로 처리 | 확인 필요 (권장) |
| Shelf·Box 목록 페이지네이션 | Room과 동일하게 cursor/limit 추가 | 확인 필요 (권장) |
| 신규 트리 엔드포인트 | M2는 지연 로딩, 필요 시 M6 | 확인 필요 (권장) |
| 이름 유일 제약 | M2는 두지 않고 UI 경고만 | 확인 필요 (권장) |
| 수동 정렬 | M2 제외, `sort_order`는 별도 ADR | 확인 필요 (권장) |
| 감사 로그 시점 | M2는 구조화 로그, `audit_log`는 M4 | 확인 필요 (권장) |
| 재고 삭제 차단 시점 | M3에서 가드 확장과 동시에 활성화 | 확인 필요 (권장) |
| 설명 최대 길이 | 2000자, OpenAPI에 maxLength 추가 | 확인 필요 (권장) |

### 11.3 ADR 후보

- ADR-017 (제안): 계층 API의 `OWNER` 표현과 `isShared` 의미. API enum과 DB enum을 분리하는 결정을 기록합니다.
- ADR-018 (제안): soft delete 삭제 차단과 부모 잠금 순서. 동시 생성·이동·삭제 규칙을 기록합니다.
- 결정을 구현 전에 확정하지 못하면 11.2번 항목의 "확인 필요" 항목을 그대로 구현하고 PR 설명에 남깁니다.

## 12. M3로의 연결

- M3는 `inventory` 테이블을 추가한 직후 `HierarchyDeleteGuard`에 재고 존재 검사를 넣고, `Room`·`Shelf`·`Box` 응답에 `inventoryCount`를 추가합니다.
- M3의 Inventory 이동은 M2의 위치 ID와 `AccessService`를 재사용하며, 이동 이력은 `inventory_movement`에 기록합니다.
- M4는 `NoGrantLookup`을 SQL 구현으로 교체하고 `share`, `share_invitation`, `GET /shared`, 초대 UI를 추가합니다. M2의 `effectivePermission`, `isShared`, 403 경로, 404 규칙은 그대로 유지됩니다.
- M4의 공유 `MANAGE`가 계층을 변경하지 못하게 하는 규칙은 M2의 `requireOwner`가 이미 담당합니다.
- M6 검색은 M2의 위치 ID와 인덱스를 조인해 하위 위치 조건을 구성합니다.

다음 마일스톤 범위는 [구현 로드맵](../implementation-roadmap.md)의 M3 항목을 따릅니다.
