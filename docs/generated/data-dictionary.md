# 데이터 사전

> 상태: 설계 초안
> 역할: 테이블, 컬럼, 타입, 제약, 인덱스, enum, 관계의 단일 원본
> 주의: 이 문서는 설계 단계의 초안입니다. 구현 착수 후 스키마 introspection으로 재생성하며, 그 전까지는 [ADR-014](../decisions/ADR-014-generated-docs-draft.md)에 따라 수동 갱신합니다.
> 관련 문서: [도메인](../domain.md), [공유](../sharing.md), [미디어·AI](../media-ai.md), [아키텍처](../architecture.md)

## 1. 공통 규칙

- Primary key는 애플리케이션에서 생성한 UUIDv7을 사용합니다.
- 시각은 `timestamptz` UTC로 저장합니다.
- 테넌트 테이블은 `space_id`를 가집니다.
- soft delete 대상은 `deleted_at`을 가집니다.
- 문자열 식별자는 영어 snake_case를 사용합니다.
- 금액은 정수와 ISO 4217 통화 코드를 사용합니다.
- 벡터 차원은 1차에서 1408차원으로 고정합니다.
- 확장: `pg_trgm`, `vector`, `citext`
- trigram GIN과 HNSW 인덱스는 `space_id`를 선두에 둘 수 없으므로 인덱스 원칙의 예외로 합니다.
- 테넌트 필터는 부분 인덱스, ACL overfetch, iterative scan으로 보완합니다.

## 2. Enum

| Enum | 값 |
|---|---|
| `user_status` | `ACTIVE`, `LOCKED`, `DELETED` |
| `inventory_status` | `IN_USE`, `STORED`, `DISPOSED` |
| `permission` | `VIEW`, `EDIT`, `MANAGE` |
| `invitation_status` | `PENDING`, `ACCEPTED`, `DECLINED`, `REVOKED`, `EXPIRED` |
| `image_status` | `UPLOADING`, `DRAFT`, `PENDING`, `PROCESSING`, `READY`, `FAILED`, `DELETED` |
| `ai_job_type` | `RECOGNITION`, `EMBEDDING` |
| `ai_job_status` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `outbox_status` | `PENDING`, `PUBLISHED`, `FAILED` |
| `embedding_source` | `MASTER`, `INVENTORY` |

## 3. 사용자와 테넌트

### `app_user`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 사용자 ID |
| `email` | citext | NOT NULL | 로그인 이메일 |
| `password_hash` | text | NOT NULL | Argon2id 해시 |
| `display_name` | text | NOT NULL | 표시 이름 |
| `status` | user_status | NOT NULL, DEFAULT `ACTIVE` | 계정 상태 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

인덱스:

- `UNIQUE (email)` where `deleted_at IS NULL`

### `user_identity`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 식별자 ID |
| `user_id` | uuid | FK app_user, NOT NULL | 사용자 |
| `provider` | text | NOT NULL | `LOCAL`, `GOOGLE`, `KAKAO` 등 |
| `provider_user_id` | text | NOT NULL | provider 사용자 ID |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |

제약:

- `UNIQUE (provider, provider_user_id)`

### `space`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Space ID |
| `owner_user_id` | uuid | FK app_user, UNIQUE, NOT NULL | 소유자 |
| `name` | text | NOT NULL | Space 이름 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

- MVP는 사용자당 하나의 Space를 가집니다.
- `owner_user_id`의 UNIQUE 제약으로 개인 Space를 보장합니다.

### `invite_code`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 초대코드 ID |
| `code_hash` | text | UNIQUE, NOT NULL | 코드 해시 |
| `label` | text | NULL | 운영 메모 |
| `max_uses` | integer | NOT NULL, CHECK > 0 | 최대 사용 횟수 |
| `used_count` | integer | NOT NULL, DEFAULT 0, CHECK >= 0 | 사용 횟수 |
| `expires_at` | timestamptz | NULL | 만료 시각 |
| `revoked_at` | timestamptz | NULL | 철회 시각 |
| `created_by` | uuid | FK app_user, NULL | 생성자 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |

제약:

- `CHECK (used_count <= max_uses)`

### `invite_code_use`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 사용 기록 ID |
| `invite_code_id` | uuid | FK invite_code, NOT NULL | 초대코드 |
| `user_id` | uuid | FK app_user, NOT NULL | 사용자 |
| `used_at` | timestamptz | NOT NULL | 사용 시각 |

제약:

- `UNIQUE (invite_code_id, user_id)`

### `password_reset_token`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 토큰 ID |
| `user_id` | uuid | FK app_user, NOT NULL | 사용자 |
| `token_hash` | text | UNIQUE, NOT NULL | 토큰 해시 |
| `expires_at` | timestamptz | NOT NULL | 만료 시각 |
| `used_at` | timestamptz | NULL | 사용 시각 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |

## 4. 계층

### `room`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Room ID |
| `space_id` | uuid | FK space, NOT NULL | 테넌트 |
| `name` | text | NOT NULL | 이름 |
| `description` | text | NULL | 설명 |
| `version` | bigint | NOT NULL, DEFAULT 0 | 낙관적 잠금 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

제약:

- `UNIQUE (id, space_id)`

### `shelf`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Shelf ID |
| `space_id` | uuid | NOT NULL | 테넌트 |
| `room_id` | uuid | NOT NULL | 상위 Room |
| `name` | text | NOT NULL | 이름 |
| `description` | text | NULL | 설명 |
| `version` | bigint | NOT NULL, DEFAULT 0 | 낙관적 잠금 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

제약:

- `UNIQUE (id, space_id)`
- `FOREIGN KEY (room_id, space_id) REFERENCES room(id, space_id)`

### `box`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Box ID |
| `space_id` | uuid | NOT NULL | 테넌트 |
| `shelf_id` | uuid | NOT NULL | 상위 Shelf |
| `name` | text | NOT NULL | 이름 |
| `description` | text | NULL | 설명 |
| `version` | bigint | NOT NULL, DEFAULT 0 | 낙관적 잠금 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

제약:

- `UNIQUE (id, space_id)`
- `FOREIGN KEY (shelf_id, space_id) REFERENCES shelf(id, space_id)`

## 5. 물품과 재고

### `category`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Category ID |
| `space_id` | uuid | FK space, NOT NULL | 테넌트 |
| `name` | text | NOT NULL | 표시 이름 |
| `normalized_name` | text | NOT NULL | 소문자·공백 정규화 이름 |
| `color` | text | NULL | UI 색상 |
| `sort_order` | integer | NOT NULL, DEFAULT 0 | 정렬 순서 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

제약:

- `UNIQUE (id, space_id)`
- `UNIQUE (space_id, normalized_name)` where `deleted_at IS NULL`

### `item`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Item ID |
| `space_id` | uuid | FK space, NOT NULL | 테넌트 |
| `name` | text | NOT NULL | 마스터 이름 |
| `description` | text | NULL | 마스터 설명 |
| `category_id` | uuid | NULL | 기본 카테고리 |
| `price_amount` | integer | NULL, CHECK >= 0 | 기본 가격 |
| `currency` | char(3) | NOT NULL, DEFAULT `KRW` | 통화 |
| `embedding` | vector(1408) | NULL | 대표 사진 임베딩 |
| `embedding_model` | text | NULL | 모델 이름 |
| `embedding_version` | text | NULL | 모델 버전 |
| `version` | bigint | NOT NULL, DEFAULT 0 | 낙관적 잠금 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

제약:

- `UNIQUE (id, space_id)`
- `FOREIGN KEY (category_id, space_id) REFERENCES category(id, space_id)`
- MVP 애플리케이션은 `currency = 'KRW'`만 허용합니다.

인덱스:

- `(space_id, category_id)`
- `(space_id, updated_at DESC)`
- `GIN (lower(name) gin_trgm_ops)`

### `inventory`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Inventory ID |
| `space_id` | uuid | FK space, NOT NULL | 테넌트 |
| `item_id` | uuid | NOT NULL | Item |
| `room_id` | uuid | NULL | 직접 위치 |
| `shelf_id` | uuid | NULL | 직접 위치 |
| `box_id` | uuid | NULL | 직접 위치 |
| `name_override` | text | NULL | 이름 오버라이드 |
| `description_override` | text | NULL | 설명 오버라이드 |
| `category_id_override` | uuid | NULL | 카테고리 오버라이드 |
| `price_amount_override` | integer | NULL, CHECK >= 0 | 가격 오버라이드 |
| `status` | inventory_status | NOT NULL, DEFAULT `STORED` | 상태 |
| `effective_embedding` | vector(1408) | NULL | 유효 임베딩 |
| `embedding_source` | embedding_source | NULL | `MASTER` 또는 `INVENTORY` |
| `embedding_model` | text | NULL | 모델 이름 |
| `embedding_version` | text | NULL | 모델 버전 |
| `embedding_updated_at` | timestamptz | NULL | 임베딩 갱신 시각 |
| `version` | bigint | NOT NULL, DEFAULT 0 | 낙관적 잠금 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

제약:

- `UNIQUE (id, space_id)`
- `CHECK (num_nonnulls(room_id, shelf_id, box_id) = 1)`
- `FOREIGN KEY (item_id, space_id) REFERENCES item(id, space_id)`
- `FOREIGN KEY (room_id, space_id) REFERENCES room(id, space_id)`
- `FOREIGN KEY (shelf_id, space_id) REFERENCES shelf(id, space_id)`
- `FOREIGN KEY (box_id, space_id) REFERENCES box(id, space_id)`
- `FOREIGN KEY (category_id_override, space_id) REFERENCES category(id, space_id)`

인덱스:

- `(space_id, item_id)`
- `(space_id, status)`
- `(space_id, category_id_override)`
- `(space_id, room_id)`
- `(space_id, shelf_id)`
- `(space_id, box_id)`
- `(space_id, updated_at DESC)`
- `HNSW (effective_embedding vector_cosine_ops)`

## 6. 이미지

### `image`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Image ID |
| `space_id` | uuid | FK space, NOT NULL | 테넌트 |
| `item_id` | uuid | NULL | Item 소속 |
| `inventory_id` | uuid | NULL | Inventory 소속 |
| `object_key` | text | UNIQUE, NOT NULL | 원본 객체 키 |
| `thumbnail_key` | text | NULL | 썸네일 객체 키 |
| `declared_mime_type` | text | NOT NULL | 클라이언트가 선언한 MIME |
| `declared_size_bytes` | bigint | NOT NULL, CHECK > 0 | 클라이언트가 선언한 크기 |
| `mime_type` | text | NULL | 검증된 MIME |
| `size_bytes` | bigint | NULL, CHECK > 0 | 검증된 크기 |
| `width` | integer | NULL, CHECK > 0 | 너비 |
| `height` | integer | NULL, CHECK > 0 | 높이 |
| `sha256` | char(64) | NULL | 콘텐츠 해시 |
| `is_primary` | boolean | NOT NULL, DEFAULT false | 대표 사진 여부 |
| `status` | image_status | NOT NULL, DEFAULT `UPLOADING` | 처리 상태 |
| `upload_expires_at` | timestamptz | NOT NULL | 업로드·DRAFT 만료 시각 |
| `embedding` | vector(1408) | NULL | 이미지 임베딩 |
| `embedding_model` | text | NULL | 모델 이름 |
| `embedding_version` | text | NULL | 모델 버전 |
| `created_by` | uuid | FK app_user, NOT NULL | 업로드 사용자 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `deleted_at` | timestamptz | NULL | soft delete 시각 |

제약:

- `UNIQUE (id, space_id)`
- `CHECK (num_nonnulls(item_id, inventory_id) <= 1)`
- `CHECK (status IN ('UPLOADING', 'DRAFT') OR num_nonnulls(item_id, inventory_id) = 1)`
- `CHECK (NOT is_primary OR num_nonnulls(item_id, inventory_id) = 1)`
- `CHECK (status <> 'READY' OR (mime_type IS NOT NULL AND size_bytes IS NOT NULL AND width IS NOT NULL AND height IS NOT NULL AND sha256 IS NOT NULL))`
- `FOREIGN KEY (item_id, space_id) REFERENCES item(id, space_id)`
- `FOREIGN KEY (inventory_id, space_id) REFERENCES inventory(id, space_id)`

인덱스:

- `UNIQUE (item_id) WHERE is_primary AND deleted_at IS NULL`
- `UNIQUE (inventory_id) WHERE is_primary AND deleted_at IS NULL`
- `(space_id, status)`
- `(space_id, inventory_id)`
- `(space_id, item_id)`
- `(space_id, sha256)`
- `(status, upload_expires_at)`

- `UPLOADING`은 presign 직후 상태이며 대상이 있거나 DRAFT 대상 없이 존재할 수 있습니다.
- `DRAFT`는 업로드 검증 후 대상이 아직 연결되지 않은 임시 이미지입니다.
- `READY`가 되기 전에는 해상도와 해시가 없을 수 있습니다.

## 7. 공유

### `share`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Share ID |
| `space_id` | uuid | FK space, NOT NULL | 리소스 소유 Space |
| `room_id` | uuid | NULL | Room 공유 |
| `shelf_id` | uuid | NULL | Shelf 공유 |
| `grantee_user_id` | uuid | FK app_user, NOT NULL | 공유 대상 |
| `permission` | permission | NOT NULL | 권한 |
| `granted_by` | uuid | FK app_user, NOT NULL | 부여자 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |
| `revoked_at` | timestamptz | NULL | 철회 시각 |

제약:

- `CHECK (num_nonnulls(room_id, shelf_id) = 1)`
- `FOREIGN KEY (room_id, space_id) REFERENCES room(id, space_id)`
- `FOREIGN KEY (shelf_id, space_id) REFERENCES shelf(id, space_id)`
- `UNIQUE (room_id, grantee_user_id) WHERE revoked_at IS NULL AND room_id IS NOT NULL`
- `UNIQUE (shelf_id, grantee_user_id) WHERE revoked_at IS NULL AND shelf_id IS NOT NULL`

인덱스:

- `(grantee_user_id, permission) WHERE revoked_at IS NULL`
- `(space_id, room_id)`
- `(space_id, shelf_id)`

### `share_invitation`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 초대 ID |
| `space_id` | uuid | FK space, NOT NULL | 리소스 소유 Space |
| `room_id` | uuid | NULL | Room 초대 |
| `shelf_id` | uuid | NULL | Shelf 초대 |
| `invited_email` | citext | NOT NULL | 초대 이메일 |
| `invited_user_id` | uuid | FK app_user, NULL | 가입된 사용자 |
| `permission` | permission | NOT NULL | 초대 권한 |
| `token_hash` | text | UNIQUE, NOT NULL | 초대 토큰 해시 |
| `status` | invitation_status | NOT NULL, DEFAULT `PENDING` | 상태 |
| `expires_at` | timestamptz | NOT NULL | 만료 시각 |
| `created_by` | uuid | FK app_user, NOT NULL | 초대자 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `accepted_at` | timestamptz | NULL | 수락 시각 |
| `declined_at` | timestamptz | NULL | 거절 시각 |
| `revoked_at` | timestamptz | NULL | 철회 시각 |

제약:

- `CHECK (num_nonnulls(room_id, shelf_id) = 1)`
- `FOREIGN KEY (room_id, space_id) REFERENCES room(id, space_id)`
- `FOREIGN KEY (shelf_id, space_id) REFERENCES shelf(id, space_id)`

인덱스:

- `(space_id, status)`
- `(invited_email, status)`
- `UNIQUE (room_id, invited_email) WHERE status = 'PENDING' AND room_id IS NOT NULL`
- `UNIQUE (shelf_id, invited_email) WHERE status = 'PENDING' AND shelf_id IS NOT NULL`

## 8. 비동기와 AI

### `outbox_event`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 이벤트 ID |
| `space_id` | uuid | FK space, NULL | 테넌트 |
| `aggregate_type` | text | NOT NULL | 애그리거트 종류 |
| `aggregate_id` | uuid | NOT NULL | 애그리거트 ID |
| `event_type` | text | NOT NULL | 이벤트 타입 |
| `payload` | jsonb | NOT NULL | 이벤트 본문 |
| `status` | outbox_status | NOT NULL, DEFAULT `PENDING` | 발행 상태 |
| `attempts` | integer | NOT NULL, DEFAULT 0 | 시도 횟수 |
| `available_at` | timestamptz | NOT NULL | 발행 가능 시각 |
| `published_at` | timestamptz | NULL | 발행 시각 |
| `last_error` | text | NULL | 마지막 오류 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |

인덱스:

- `(status, available_at)`
- `(space_id, event_type)`

### `ai_job`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | Job ID |
| `space_id` | uuid | FK space, NOT NULL | 테넌트 |
| `inventory_id` | uuid | FK inventory, NULL | 대상 재고 |
| `image_id` | uuid | FK image, NOT NULL | 대상 이미지 |
| `job_type` | ai_job_type | NOT NULL | 인식 또는 임베딩 |
| `status` | ai_job_status | NOT NULL, DEFAULT `PENDING` | 상태 |
| `idempotency_key` | text | UNIQUE, NOT NULL | 멱등 키 |
| `input_hash` | char(64) | NULL | 입력 이미지 해시 |
| `context_hash` | char(64) | NULL | 카테고리·모드 등 문맥 해시 |
| `cache_key` | text | NULL | Space 단위 결과 캐시 키 |
| `provider` | text | NULL | AI provider |
| `model` | text | NULL | 모델 |
| `result` | jsonb | NULL | 결과 |
| `error` | text | NULL | 오류 |
| `attempts` | integer | NOT NULL, DEFAULT 0 | 시도 횟수 |
| `input_tokens` | integer | NULL | 입력 토큰 수 |
| `output_tokens` | integer | NULL | 출력 토큰 수 |
| `cost_amount` | numeric(12,6) | NULL | 호출 비용 |
| `cost_currency` | char(3) | NULL | 비용 통화 |
| `latency_ms` | integer | NULL | 호출 지연 |
| `started_at` | timestamptz | NULL | 시작 시각 |
| `completed_at` | timestamptz | NULL | 완료 시각 |
| `cancelled_at` | timestamptz | NULL | 취소 시각 |
| `created_by` | uuid | FK app_user, NOT NULL | 요청자 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `updated_at` | timestamptz | NOT NULL | 수정 시각 |

제약:

- `FOREIGN KEY (inventory_id, space_id) REFERENCES inventory(id, space_id)`
- `FOREIGN KEY (image_id, space_id) REFERENCES image(id, space_id)`
- `CHECK (cache_key IS NULL OR status = 'COMPLETED')`

인덱스:

- `(space_id, status)`
- `(status, created_at)`
- `(image_id)`
- `UNIQUE (cache_key) WHERE cache_key IS NOT NULL`

- 인식은 `image_id`가 필수이며, `inventory_id`가 NULL이면 신규 Item 모드입니다.
- `cache_key`는 Space, 이미지 해시, 모델, 카테고리 집합 해시, 요청 모드를 조합해 만듭니다.
- `CANCELLED` job은 결과를 저장하지 않습니다.

## 9. 멱등성

### `idempotency_record`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 기록 ID |
| `user_id` | uuid | FK app_user, NOT NULL | 요청 사용자 |
| `idempotency_key` | text | NOT NULL | 클라이언트 키 |
| `request_method` | text | NOT NULL | HTTP 메서드 |
| `request_path` | text | NOT NULL | 요청 경로 |
| `request_hash` | char(64) | NOT NULL | 요청 본문 해시 |
| `response_status` | integer | NOT NULL | 저장된 응답 상태 |
| `response_body` | jsonb | NOT NULL | 저장된 응답 본문 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |
| `expires_at` | timestamptz | NOT NULL | 만료 시각 |

제약:

- `UNIQUE (user_id, idempotency_key)`
- `CHECK (expires_at > created_at)`

인덱스:

- `(expires_at)`

## 10. 이력과 알림

### `inventory_movement`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 이동 ID |
| `space_id` | uuid | FK space, NOT NULL | 테넌트 |
| `inventory_id` | uuid | FK inventory, NOT NULL | 재고 |
| `from_room_id` | uuid | NULL | 이전 Room |
| `from_shelf_id` | uuid | NULL | 이전 Shelf |
| `from_box_id` | uuid | NULL | 이전 Box |
| `to_room_id` | uuid | NULL | 이후 Room |
| `to_shelf_id` | uuid | NULL | 이후 Shelf |
| `to_box_id` | uuid | NULL | 이후 Box |
| `moved_by` | uuid | FK app_user, NOT NULL | 이동자 |
| `moved_at` | timestamptz | NOT NULL | 이동 시각 |

제약:

- `CHECK (num_nonnulls(from_room_id, from_shelf_id, from_box_id) = 1)`
- `CHECK (num_nonnulls(to_room_id, to_shelf_id, to_box_id) = 1)`
- `FOREIGN KEY (inventory_id, space_id) REFERENCES inventory(id, space_id)`
- `FOREIGN KEY (from_room_id, space_id) REFERENCES room(id, space_id)`
- `FOREIGN KEY (from_shelf_id, space_id) REFERENCES shelf(id, space_id)`
- `FOREIGN KEY (from_box_id, space_id) REFERENCES box(id, space_id)`
- `FOREIGN KEY (to_room_id, space_id) REFERENCES room(id, space_id)`
- `FOREIGN KEY (to_shelf_id, space_id) REFERENCES shelf(id, space_id)`
- `FOREIGN KEY (to_box_id, space_id) REFERENCES box(id, space_id)`

인덱스:

- `(space_id, inventory_id, moved_at DESC)`

### `audit_log`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 감사 ID |
| `space_id` | uuid | FK space, NOT NULL | 테넌트 |
| `actor_user_id` | uuid | FK app_user, NOT NULL | 행위자 |
| `action` | text | NOT NULL | 행위 |
| `resource_type` | text | NOT NULL | 리소스 종류 |
| `resource_id` | uuid | NULL | 리소스 ID |
| `metadata` | jsonb | NULL | 추가 정보 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |

인덱스:

- `(space_id, created_at DESC)`
- `(resource_type, resource_id)`

### `notification`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 알림 ID |
| `user_id` | uuid | FK app_user, NOT NULL | 수신자 |
| `space_id` | uuid | FK space, NULL | 관련 Space |
| `type` | text | NOT NULL | 알림 종류 |
| `title` | text | NOT NULL | 제목 |
| `body` | text | NOT NULL | 본문 |
| `payload` | jsonb | NULL | 이동·딥링크 정보 |
| `read_at` | timestamptz | NULL | 읽은 시각 |
| `created_at` | timestamptz | NOT NULL | 생성 시각 |

인덱스:

- `(user_id, read_at, created_at DESC)`

## 11. 검색과 추천

### `user_item_view`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `user_id` | uuid | FK app_user, PK 일부 | 사용자 |
| `item_id` | uuid | FK item, PK 일부 | Item |
| `space_id` | uuid | FK space, NOT NULL | Item의 Space |
| `view_count` | bigint | NOT NULL, DEFAULT 1 | 조회 횟수 |
| `last_viewed_at` | timestamptz | NOT NULL | 마지막 조회 |

제약:

- `PRIMARY KEY (user_id, item_id)`
- `FOREIGN KEY (item_id, space_id) REFERENCES item(id, space_id)`

인덱스:

- `(user_id, last_viewed_at DESC)`
- `(user_id, view_count DESC)`

### `recent_search`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | uuid | PK | 검색 ID |
| `user_id` | uuid | FK app_user, NOT NULL | 사용자 |
| `query` | text | NOT NULL | 원본 검색어 |
| `normalized_query` | text | NOT NULL | 정규화 검색어 |
| `last_searched_at` | timestamptz | NOT NULL | 마지막 검색 |

제약:

- `UNIQUE (user_id, normalized_query)`

인덱스:

- `(user_id, last_searched_at DESC)`

## 12. Spring Session JDBC

Spring Session JDBC가 관리하는 테이블입니다. 직접 수정하지 않습니다.

- `spring_session`
- `spring_session_attributes`

세션 만료, 속성 저장, 동시 세션 제어는 라이브러리 설정을 따릅니다.

## 13. 관계 요약

```text
app_user 1---1 space
space 1---N room
space 1---N category
space 1---N item
room 1---N shelf
shelf 1---N box
item 1---N inventory
room/shelf/box 1---N inventory
item/inventory 1---N image
room/shelf 1---N share
room/shelf 1---N share_invitation
app_user 1---N idempotency_record
image 1---N ai_job (선택)
inventory 1---N inventory_movement
```

복합 FK의 `(id, space_id)` 유니크 제약은 모든 테넌트 테이블에 필요합니다.
