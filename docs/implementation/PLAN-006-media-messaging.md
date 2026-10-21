# PLAN-006: M5 이미지·메시징 상세 구현 계획

> 상태: M5 기반 구현 착수 (V15 schema·S3 adapter·presign/complete)
> 역할: M5 이미지 수명주기, S3/MinIO, 후처리 worker, Outbox, 정리 job, 갤러리 UI 실행 계획
> 상위 문서: [구현 로드맵](../implementation-roadmap.md)
> 선행 계획: [PLAN-001 기반](PLAN-001-foundation.md), [PLAN-001 후속](PLAN-001-foundation-delivery.md)
> 관련 문서: [미디어·AI](../media-ai.md), [도메인](../domain.md), [공유](../sharing.md), [계약](../contracts.md), [화면](../frontend.md), [디자인 시스템](../design-system.md), [운영](../operations.md), [데이터 사전](../generated/data-dictionary.md), [OpenAPI](../generated/openapi.yaml), [이벤트](../generated/events.yaml), [ADR-005](../decisions/ADR-005-rabbitmq-outbox.md), [ADR-008](../decisions/ADR-008-presigned-images.md), [ADR-011](../decisions/ADR-011-image-upload-lifecycle.md), [ADR-014](../decisions/ADR-014-generated-docs-draft.md), [ADR-015](../decisions/ADR-015-contract-preview-mode.md), [ADR-016](../decisions/ADR-016-neo-brutal-design-system.md)

## 1. 목적과 범위

이미지 한 장이 업로드되어 노출되기까지의 전 구간을 vertical slice로 완성한다: presign, S3 직접 업로드, complete 검증, Outbox, RabbitMQ 소비, 재인코딩·EXIF 제거·썸네일·해시, 대표 이미지, 만료·삭제 정리, 갤러리 UI.

| 구분 | 내용 |
|---|---|
| 포함 | `image` 7개 상태, presign·complete·URL·배치 URL·대표·삭제 API, Item/Inventory/DRAFT 바인딩, 비공개 버킷·객체 키, WebP 재인코딩·EXIF 제거·512/128 썸네일·SHA-256, `image.process` worker, Outbox·confirm, S3 health, 만료·soft delete 정리, 업로드·갤러리 UI |
| 제외 | AI 인식·임베딩·이미지 검색(M7), 검색 인덱스(M6), DRAFT 연결 공개 API(M7), HEIC 서버 디코딩, 바이러스 스캔, CDN, 다중 리전, S3 수명주기 자동화, 관리자 UI |

- 이미지 규칙의 단일 원본은 [미디어·AI](../media-ai.md) 2~4번, 7번, 9번 항목이다. 전체 DDL·제품 규칙은 [데이터 사전](../generated/data-dictionary.md)과 [미디어·AI](../media-ai.md)를 링크하고 재정의하지 않는다.
- M4와 M5는 M3 이후 병렬로 진행할 수 있다. M5는 `AccessService` 포트에 의존하고 M4 병합 전에는 소유자 경로만 활성화한다.

## 2. 선행 조건

| 선행 | M5가 사용하는 것 | 없을 때 |
|---|---|---|
| M0 | 빌드, Testcontainers, `request_id`, problem+json, UUIDv7, Clock, 설정 검증, 로그 마스킹 | M0 완료 전제. 추가 기반을 만들지 않는다. |
| M1 | 세션 인증, `space_id`, CSRF, `app_user` | 테스트는 고정 사용자·Space 픽스처로 대체 |
| M2 | 계층, `AccessService` 기본 유효 권한 | 인터페이스만 소비하고 권한 단위 테스트를 먼저 둔다. |
| M3 | `item`, `inventory`, 상세·목록 API, `primaryImageId` | M3 없이는 시작하지 않는다. M5의 최소 선행은 M3다. |
| M4 | Grant 상속, 공유 사용자 `EDIT` | 병렬 가능. 공유 테스트는 `@Tag("sharing")`으로 분리하고 M4 병합 후 활성화 |

- 마이그레이션은 `V5__image.sql`, `V6__outbox_event.sql`을 제안한다. M4가 V5를 먼저 쓰면 병합 전에 V6·V7로 옮기고 적용된 마이그레이션은 수정하지 않는다. 착수 전 M3 DTO의 `primaryImageId`, M4 `AccessService` 시그니처, 로컬 MinIO CORS를 확인한다.

## 3. 데이터 모델과 마이그레이션

스키마 단일 원본은 [데이터 사전](../generated/data-dictionary.md) 6번 항목이다. M5는 아래 차이만 추가한다.

| 컬럼·제약 | 변경 |
|---|---|
| `upload_completed_at` / `processed_at` | `timestamptz NULL`. complete·READY 시각 |
| `processing_attempts` / `last_error` | `integer NOT NULL DEFAULT 0` / `text NULL`. 재시도·실패 요약(스택 트레이스, presigned URL 금지) |
| `CHECK (status <> 'DRAFT' OR num_nonnulls(item_id, inventory_id) = 0)` | DRAFT는 대상 없음 |
| `CHECK (status <> 'READY' OR thumbnail_key IS NOT NULL)` | READY는 512 썸네일 필수 |
| `CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$')` | 소문자 hex 고정 |
| `UNIQUE (thumbnail_key) WHERE thumbnail_key IS NOT NULL` | 썸네일 키 재사용 방지 |
| `CHECK (NOT is_primary OR deleted_at IS NULL)` | 삭제 대표 금지 |
| `image` 인덱스 | `(item_id, created_at DESC) WHERE deleted_at IS NULL`, `(inventory_id, created_at DESC) WHERE deleted_at IS NULL`, `(status, updated_at)`, `(status, deleted_at)` |
| `outbox_event` | `schema_version integer NOT NULL DEFAULT 1`, `(status, created_at)` |

- `thumbnail_key`는 512 키다. 128은 `thumb-128.webp`로 유도한다. `object_key`는 최종 원본 키이고 worker가 같은 키에 재인코딩 결과를 덮어쓴다. S3 PUT은 원자적이고 READY 전에는 노출하지 않는다.
- `sha256`은 complete에서 원본 기준, worker가 정규화 결과 기준으로 기록한다. `outbox_event.payload`에는 payload만 저장하고 릴레이가 `id`, `event_type`, `space_id`, `created_at`, `schema_version`으로 envelope을 조립한다. `aggregate_type=IMAGE`, `aggregate_id=imageId`다.

### 3.1 상태 전이

| From → To | 트리거·가드 | 부수 효과 |
|---|---|---|
| `UPLOADING → PENDING` | complete, 대상 수정 권한, 객체 검증 | `upload_completed_at`, `image.uploaded` |
| `UPLOADING → DRAFT` | complete, 생성자·개인 Space | `upload_completed_at`, Outbox 없음 |
| `DRAFT → PENDING` | 내부 attach, 대상 수정 권한·쿼터 | 대상 컬럼, `image.uploaded` |
| `PENDING → PROCESSING` | worker claim, `PENDING`/`FAILED`, 시도 한도 | `processing_attempts+1` |
| `PROCESSING → READY` | worker 성공, 대상 유효 | MIME·크기·해상도·해시·썸네일, `image.processed`, 대표 승격 |
| `PROCESSING → FAILED` | worker 실패, 마지막 시도·복구 불가 | `last_error`, 원본 비노출 |
| `FAILED → PROCESSING` | 재시도, `attempts < max` | 재처리 |
| 임의 상태 → `DELETED` | DELETE API, 수정 권한 또는 생성자 | soft delete, 승격, `image.deleted` |
| `DELETED` → 행 삭제 | purge, 30일 | 객체·행 삭제 |
| `UPLOADING`·`DRAFT`·`FAILED` → 행 삭제 | 만료 정리 | 객체·행 삭제. API 전이 아님 |

- `PENDING`은 complete만, `READY`는 worker만 만든다. API가 `READY`를 직접 쓰지 않는다.
- 사용자 삭제만 `DELETED`로 두고 만료 정리는 행을 물리 삭제한다. [미디어·AI](../media-ai.md) 2.1, 9번 항목과 일치시킨다. `is_primary`는 `PENDING`에서 힌트로 켤 수 있지만 URL·갤러리는 `READY`만 쓴다.

### 3.2 대상·객체 키·대표·쿼터

| 대상 | 컬럼 | presign 권한 | 원본 키 |
|---|---|---|---|
| `ITEM` | `item_id` | Space `OWNER` | `spaces/{spaceId}/item/{itemId}/{imageId}/original.webp` |
| `INVENTORY` | `inventory_id` | `EDIT` 이상 | `spaces/{spaceId}/inventory/{inventoryId}/{imageId}/original.webp` |
| `DRAFT` | 없음 | 생성자, 개인 Space | `spaces/{spaceId}/draft/{imageId}/original.webp` |

- 썸네일은 같은 prefix의 `thumb-512.webp`, `thumb-128.webp`다. worker 임시 키는 `spaces/{spaceId}/tmp/{imageId}/{attempt}.webp`이고 성공·실패 후 삭제한다.
- 키는 서버의 `ImageObjectKey`가 UUID로만 조립한다. complete는 `objectKey`가 행의 `object_key`와 정확히 같은지 확인한다. 키는 최대 512자, `[a-z0-9-/.]`만 허용하고 `..`를 거부한다.
- 대표는 대상별 1장이고 partial unique index가 최종 방어선이다. 첫 `READY`가 자동 승격하며, 수동 변경은 `READY`만 허용하고 대상 행을 `FOR UPDATE`로 잠근다. 삭제·실패 시 최신 `READY`를 `ORDER BY created_at DESC, id DESC`로 승격한다.
- 쿼터는 `deleted_at IS NULL` 행 기준 10장이며 `UPLOADING`·`FAILED`도 포함한다. 검사는 대상 부모 행 `FOR UPDATE`, DRAFT는 사용자 advisory lock·20장이다. 삭제는 `deleted_at`을 즉시 설정해 쿼터를 해제한다.

### 3.3 마이그레이션

- `V15__image.sql`. Outbox는 M4 `V14`를 재사용한다. expand만 수행하고 backfill은 없다. 적용 후 `bash scripts/docs_generate.sh schema`로 데이터 사전을 재생성하고, 생성기 전환이 전이면 [ADR-014](../decisions/ADR-014-generated-docs-draft.md)에 따라 초안을 수동 갱신한다.
- 검증: `./gradlew :backend:test --tests "com.agolock.media.ImageSchemaTest"`, `python3 -B scripts/docs_check.py`.

## 4. 백엔드·미디어·워커·메시징 설계

### 4.1 패키지·포트·설정

| 경로 | 책임 |
|---|---|
| `backend/src/main/kotlin/com/agolock/media/api/`, `domain/` | `ImageController`, `ImageDtos`, `Image`, `ImageStatus`, `ImageTargetType` |
| `backend/src/main/kotlin/com/agolock/media/service/` | `ImagePresignService`, `ImageCompleteService`, `ImageUrlService`, `ImagePrimaryService`, `ImageDeleteService`, `ImageAttachmentService`, `ImageQuotaService` |
| `backend/src/main/kotlin/com/agolock/media/storage/` | `ImageObjectKey`, `ImageStoragePort`, `S3ImageStorageAdapter`, `S3HealthIndicator` |
| `backend/src/main/kotlin/com/agolock/media/process/`, `worker/` | `ImageProcessorPort`, `WebpImageProcessor`, `ImageProcessConsumer`, `ImageProcessService` |
| `backend/src/main/kotlin/com/agolock/media/cleanup/`, `persistence/` | `ImageExpiryJob`, `ImagePurgeJob`, `ImageStuckJob`, `ImageMapper` |
| `backend/src/main/resources/mapper/media/ImageMapper.xml` | SQL. `mybatis.mapper-locations`에 `classpath*:/mapper/**/*.xml` 추가 |
| `backend/src/main/kotlin/com/agolock/messaging/outbox/`, `rabbit/` | `OutboxRelay`, `OutboxCleanupJob`, `RabbitTopologyConfig`, `PublisherConfirmConfig` |
| `backend/src/main/kotlin/com/agolock/common/config/SchedulingConfig.kt` | `@EnableScheduling`, worker 스케줄 |

- 외부 연동은 `ImageStoragePort`(`presignPut`, `presignGet`, `head`, `openStream`, `put`, `delete`)와 `ImageProcessorPort.process(bytes)` 뒤에 둔다. 후자는 `originalBytes`, `thumb512Bytes`, `thumb128Bytes`, `width`, `height`, `sha256`을 반환한다.
- 설정: `agolock.storage.endpoint`/`public-endpoint`(`http://localhost:9000`), `region`/`bucket`/`path-style-access`(`us-east-1`/`agolock-local`/`true`), `media.presign-ttl` 5분, `upload-expiry`/`failed-expiry`/`deleted-retention` 24시간/7일/30일, `max-size`/`max-per-target`/`max-draft-per-user` 10MB/10/20, `max-edge`/`max-pixels`/`webp-quality`/`thumbnails` 2048/40MP/0.8/512,128, `worker-concurrency` 4, `outbox.batch-size`/`lease`/`max-attempts` 50/30초/10.
- AWS SDK v2 `S3Client`, `S3Presigner`를 쓰고 `gradle/libs.versions.toml`에 `software.amazon.awssdk:bom`, `s3`, `url-connection-client`를 추가한다. 버킷은 비공개이며 `infra/minio/init.sh`의 `mc anonymous set none`과 운영 public access block을 유지한다.
- 브라우저 PUT을 위해 `infra/minio/cors.json`(로컬·staging·production origin, `PUT`/`GET`/`HEAD`, `content-type`·`x-amz-*`, `ETag`, 3600초)을 `minio-init`에 마운트하고 `init.sh`에서 적용한다. MinIO `mc` 버전에 따라 `mc cors set` 또는 `mc admin config set api`를 쓰고 `OPTIONS`로 확인한다.
- 운영 IAM은 `s3:GetObject`, `PutObject`, `DeleteObject`, `AbortMultipartUpload`, `ListBucket`만 허용한다. `S3HealthIndicator`는 `headBucket` 1초 타임아웃·10초 캐시로 readiness `s3` 그룹에 넣고 상세 오류는 숨긴다. `LogMasker`에 `X-Amz-Signature`, `X-Amz-Credential`을 추가한다.

### 4.2 Presign과 Complete

- Presign: 세션·Space 확인 → `targetType`/`targetId` 검증(DRAFT는 ID 없음) → 대상 조회·권한(Item OWNER, Inventory EDIT+, DRAFT 생성자) → WebP·1~10MB 검증 → 부모 행 `FOR UPDATE`·쿼터 → `imageId`(UUIDv7)·`objectKey`·`upload_expires_at=now+24h` → `UPLOADING` insert → 5분 presigned PUT(`Content-Type`, `Content-Length` 서명 포함) → `imageId`, `url`, `objectKey`, `expiresAt`, `uploadExpiresAt` 반환.
- Presign은 DB 트랜잭션과 서명만 하고 S3 호출을 하지 않는다. 같은 `Idempotency-Key`·같은 본문은 5분 내 재생하고, 다르면 `409 IDEMPOTENCY_KEY_REUSED`다. 레이트리밋은 사용자당 시간당 30회다.
- Complete: 권한 재확인 → `UPLOADING` 확인(같은 키의 `PENDING`/`PROCESSING`/`READY`는 202 재생) → `upload_expires_at` 확인 → `objectKey` 정확 일치 → `HeadObject` 크기·타입 → 앞 16바이트 `RIFF`(0~3)·`WEBP`(8~11) → 전체 SHA-256 → `mime_type`/`size_bytes`/`sha256`/`upload_completed_at` 기록 → DRAFT는 `DRAFT`, 대상은 `PENDING` → 같은 트랜잭션에 `image.uploaded` Outbox → 202.
- 오류는 `IMAGE_UPLOAD_EXPIRED`(409), `IMAGE_KEY_MISMATCH`(422), `IMAGE_OBJECT_NOT_FOUND`(422), `IMAGE_TOO_LARGE`(422), `IMAGE_FORMAT_UNSUPPORTED`(422)다. `isPrimary=true`이고 대표가 없으면 `is_primary=true` 힌트만 둔다.
- Outbox insert 실패 시 상태 변경도 롤백하고 객체는 남겨 complete를 재시도한다. complete 해시는 원본 기준이며 worker가 정규화 결과로 덮어쓴다.

### 4.3 후처리 worker

| 단계 | 처리 |
|---|---|
| claim | `UPDATE image SET status='PROCESSING', processing_attempts=processing_attempts+1 WHERE id=? AND status IN ('PENDING','FAILED')`가 1행일 때만 진행. 0행이면 중복으로 보고 ack |
| 로드·검증 | 메시지 `objectKey` 대신 DB `object_key` 사용. 없거나 `DELETED`면 객체 삭제 후 종료. 매직 바이트, 10MB, `maxPixels` 40MP, 애니메이션 WebP 거부 |
| 재인코딩·EXIF | 긴 변 2048px, WebP quality 0.8, 알파 유지. `IIOImage(image, null, null)`로 메타데이터 없이 쓰고 `EXIF`·`XMP` 청크가 없는지 확인 |
| 썸네일·해시 | 긴 변 512·128 WebP(비율 유지, 자르지 않음), 정규화 원본 SHA-256 |
| 업로드·저장 | `original.webp` 덮어쓰기, 썸네일 쓰기, 임시 키 삭제. `READY`, MIME·크기·해상도·해시·썸네일·`processed_at`을 한 트랜잭션으로 갱신 |
| 이벤트 | 같은 트랜잭션에 `image.processed` Outbox. 대표가 없으면 승격. 커밋 후 ack |

- worker는 `worker` 프로필에서만 활성화한다. `application-worker.yml`은 `spring.main.web-application-type: none`, `agolock.media.worker-concurrency`를 쓴다.
- 코덱은 `com.github.usefulness:webp-imageio`와 `org.sejda.imageio:webp-imageio` 중 ARM64·Java 21 검증 후 하나를 고정한다. EXIF 검증은 `com.drewnoakes:metadata-extractor`를 테스트 범위에 둔다.
- 실패는 `REQUIRES_NEW`로 `FAILED`, `last_error`(500자), `updated_at`을 기록하고 예외를 던진다. Spring Retry 최대 5회, 1초·2배·최대 30초, 최종 실패는 `default-requeue-rejected=false`와 DLX로 `image.process.dlq`에 보낸다. 멱등성은 `imageId` 상태 전이로 보장한다.
- 처리 중 삭제로 READY 갱신이 0행이면 객체를 지우고 ack한다. `PROCESSING` 30분 초과는 `ImageStuckJob`이 `FAILED`로 표시하고 알림을 보낸다.

### 4.4 대표·삭제·Outbox·정리

- 대표 변경은 `READY`·같은 Space 확인 후 대상 행을 잠그고 기존 대표 해제, 새 대표 설정. 자동 승격은 최신 `READY` conditional update, unique 위반은 no-op.
- 삭제는 `status='DELETED', deleted_at=now(), is_primary=false`. 대표였다면 같은 트랜잭션에서 승격하고 `image.deleted` Outbox. 객체는 30일 purge가 지운다. DRAFT 연결은 `ImageAttachmentService.attach`가 행을 잠그고 쿼터 확인 후 `PENDING`·`image.uploaded`를 만든다. 공개 API는 M7이다.
- Outbox는 `agolock.events` topic, routing key `eventType`. 500ms마다 `FOR UPDATE SKIP LOCKED`로 `batch-size`만큼 가져와 `available_at`을 `lease`만큼 미래로 갱신한다. `CorrelationData(eventId)`, `mandatory=true`, confirm 2초 대기. 성공은 `PUBLISHED`, 실패는 `attempts+1`·backoff·`FAILED`(max 초과)다. lease 만료 후 재획득하며 at-least-once 중복은 소비자가 흡수한다.
- `OutboxCleanupJob`은 `PUBLISHED` 7일, `FAILED` 30일 후 삭제한다. `FAILED`는 자동 재시도하지 않고 운영 CLI가 `PENDING`으로 되돌린다.

| job | 주기 | 대상 | 처리 |
|---|---|---|---|
| `ImageExpiryJob` | 10분 | `UPLOADING`·`DRAFT` 만료, `FAILED` 7일 | 객체·행 삭제. M7 `ai_job` 참조 시 ADR 필요 |
| `ImagePurgeJob` | 1시간 | `DELETED` 30일 | 객체·행 삭제 |
| `ImageStuckJob` | 10분 | `PROCESSING` 30분 | `FAILED`, 알림 |
| `OutboxCleanupJob` | 1시간 | `PUBLISHED` 7일, `FAILED` 30일 | 행 삭제 |

- 정리 job은 `worker` 프로필에서만 실행하고 `image_cleanup_deleted_total{reason}`을 기록한다. S3 404는 성공, S3 오류는 다음 주기에 재시도한다. API와 worker는 같은 jar를 쓰고 `SPRING_PROFILES_ACTIVE`만 다르며 worker는 웹을 띄우지 않는다.

## 5. API 계약

Base path `/api/v1`, 오류는 `application/problem+json`. 스키마·operationId의 단일 원본은 [OpenAPI](../generated/openapi.yaml)다.

| Method·Path | 권한 | 요청 | 응답·오류 |
|---|---|---|---|
| `POST /images/presign` | Item `OWNER`, Inventory `EDIT` 이상, DRAFT 생성자 | `targetType`, `targetId?`, `contentType`, `sizeBytes` | `200` `imageId`, `uploadUrl`, `objectKey`, `expiresAt`, `uploadExpiresAt` |
| `POST /images/{imageId}/complete` | presign과 동일 | `objectKey`, `isPrimary?` | `202` `Image` |
| `GET /images/{imageId}/url` | `VIEW` 이상 | `variant=THUMB\|THUMB_SMALL\|ORIGINAL` | `200` `url`, `expiresAt`, `variant` |
| `POST /images/urls` | 이미지별 `VIEW` 이상 | `imageIds` 최대 100, `variant?` | `200` `items`, `unavailableImageIds?` |
| `POST /images/{imageId}/primary` | Item `OWNER`, Inventory `EDIT` 이상 | 없음 | `200` `Image` |
| `DELETE /images/{imageId}` | 수정 권한 또는 UPLOADING/DRAFT 생성자 | 없음 | `204` |
| `GET /items/{itemId}/images` | `VIEW` 이상 | 없음 | `200` `ImageList` |
| `GET /inventories/{inventoryId}/images` | `VIEW` 이상 | 없음 | `200` `ImageList` |

- 목록은 presigned URL 없이 `id`, `status`, `isPrimary`, `width`, `height`, `mimeType`, `uploadExpiresAt`, `createdAt`만 반환한다. 정렬은 `is_primary DESC, created_at DESC, id DESC`, `DELETED` 제외·`FAILED` 포함.
- 배치는 접근 가능한 `READY`만 반환하고 나머지는 `unavailableImageIds`에 넣는다(additive). 단건 URL은 `READY`가 아니면 `409 IMAGE_NOT_READY`, `FAILED`면 `409 IMAGE_PROCESSING_FAILED`, `THUMB_SMALL`이 없으면 512로 fallback한다.
- `primary`는 `READY`가 아니면 409, 이미 대표면 200 no-op. `DELETE`는 이미 `DELETED`면 no-op, `PROCESSING` 중에도 허용한다. DRAFT에 `targetId`를 보내면 `422 IMAGE_TARGET_INVALID`.
- URL 응답은 `Cache-Control: no-store`이고 presigned URL은 로그에 남기지 않는다.

| 작업 | Item | Inventory | DRAFT |
|---|---|---|---|
| presign·complete | Space `OWNER` | `EDIT` 이상 | 생성자 본인 |
| 목록·URL | `VIEW` 이상 | `VIEW` 이상 | 생성자 본인 |
| 대표·삭제 | `OWNER` | `EDIT` 이상 | 생성자 본인(UPLOADING/DRAFT) |

- 공유 사용자의 `EDIT`는 Inventory 이미지에만 적용되고 Item 이미지에는 적용되지 않는다. 공유 철회 후 새 URL은 발급하지 않고 이미 발급된 URL은 TTL까지만 유효하다. 타 Space 이미지 ID는 `404`다. UI는 `effectivePermission`, `isOwner`로 버튼을 숨기지만 서버가 최종 판정한다.

| 항목 | 값 |
|---|---|
| 업로드·다운로드 URL | 5분 |
| UPLOADING·DRAFT 만료 / FAILED 정리 / soft delete 보관 | 24시간 / 7일 / 30일 |
| 최대 크기·대상별 장수·배치 | 10MB·10장·100개 |
| presign 레이트리밋 | 사용자당 시간당 30회 |

| 코드 | HTTP | 의미 |
|---|---|---|
| `IMAGE_FORMAT_UNSUPPORTED` / `IMAGE_TOO_LARGE` | 422 | WebP 아님·10MB 초과·0바이트 |
| `IMAGE_UPLOAD_EXPIRED` | 409 | 업로드 만료 |
| `IMAGE_OBJECT_NOT_FOUND` / `IMAGE_KEY_MISMATCH` | 422 | 객체 없음·objectKey 불일치 |
| `IMAGE_NOT_READY` / `IMAGE_PROCESSING_FAILED` | 409 | READY 아님·후처리 실패 |
| `IMAGE_LIMIT_EXCEEDED` / `IMAGE_INVALID_STATE` | 409 | 10장 초과·잘못된 전이 |
| `IMAGE_TARGET_INVALID` | 422 | 대상 조합 오류 |
| `IMAGE_STORAGE_UNAVAILABLE` | 503 | S3 일시 장애 |

- 기존 `PERMISSION_DENIED`, `RESOURCE_NOT_FOUND`, `VALIDATION_FAILED`, `IDEMPOTENCY_KEY_REUSED`를 함께 쓴다. 새 코드는 [계약](../contracts.md) 3.2와 OpenAPI 오류 응답에 반영한다.

## 6. 이벤트 계약

단일 원본은 [이벤트](../generated/events.yaml)와 `backend/src/main/resources/asyncapi/events.yaml`이다.

| 큐 | 바인딩 | 소비자 | DLQ |
|---|---|---|---|
| `image.process` | `image.uploaded` | M5 worker | `image.process.dlq` |
| `domain.events` | `image.processed`, `image.deleted` | M6 검색·감사 | `domain.events.dlq` |

- exchange `agolock.events`, DLX `agolock.dlx`. 큐 인자는 `x-dead-letter-exchange=agolock.dlx`, `x-dead-letter-routing-key=<queue>.dlq`.
- envelope은 `eventId`, `eventType`, `spaceId`, `occurredAt`, `schemaVersion`, `payload`이고 `eventId`는 Outbox 행 ID다.
- `image.uploaded`는 complete(대상)와 DRAFT 연결 시, `image.processed`는 READY와 같은 트랜잭션, `image.deleted`는 사용자 soft delete에서 발행한다. 만료 정리는 이벤트를 만들지 않는다.

| 이벤트 | payload 필수 |
|---|---|
| `image.uploaded` | `imageId`, `objectKey`, `targetType`, `targetId`, `declaredSizeBytes` |
| `image.processed` | `imageId`, `targetType`, `targetId`, `width`, `height`, `sha256`, `thumbnailKey`, `isPrimary` |
| `image.deleted` | `imageId`, `targetType`, `targetId`, `wasPrimary`, `promotedImageId?` |

- payload에 presigned URL, 원본 바이트, EXIF, 사용자 이메일을 넣지 않는다. `schemaVersion`은 1로 시작하고 필드 추가는 유지, 삭제·의미 변경은 새 이벤트나 버전 2로 분리한다.
- `image.process` 소비자는 `imageId` 상태 전이로, `domain.events` 소비자는 `eventId` 저장·비교로 멱등성을 보장한다. M5는 전자만, M6가 후자를 구현한다.
- `ImageProcessRequestedPayload`에 `targetType`, `targetId`, `sizeBytes`를 추가하고 `DomainEventPayload` enum에 `image.deleted`를 추가한다. `bash scripts/docs_generate.sh events`와 `python3 -B scripts/docs_check.py`를 실행한다.

## 7. 프론트엔드

| 경로 | 책임 |
|---|---|
| `frontend/src/features/media/api/image-api.ts` / `image-queries.ts` | presign·complete·url·batch·primary·delete, Query 키·무효화·폴링 |
| `frontend/src/features/media/lib/convert-to-webp.ts` / `validate-image.ts` | Canvas/OffscreenCanvas 변환, 형식·크기·해상도 검증 |
| `frontend/src/features/media/hooks/use-image-upload.ts` / `use-image-urls.ts` | 상태 머신·진행률·취소, 배치 URL 캐시·만료 |
| `frontend/src/features/media/components/image-uploader.tsx` / `image-gallery.tsx` / `image-crop-dialog.tsx` | 파일·카메라·진행, 썸네일·대표·삭제, 4:3 크롭·키보드 |
| `frontend/src/features/inventory/inventory-detail-page.tsx` / `inventory-edit-page.tsx` | 히어로·갤러리, 업로드·대표·삭제 연결 |
| `frontend/src/features/items/item-detail-page.tsx` / `item-create-page.tsx` | 소유자 전용 Item 갤러리, 신규 Item 첫 이미지 |

- 화면·흐름은 [화면](../frontend.md) 3~6번, 시각은 [디자인 시스템](../design-system.md) 6~7번을 따른다. 둥근 모서리·블러 섀도·이모지를 쓰지 않고 `Card`, `Badge`, `Button`, `Thumbnail`을 재사용한다.
- 업로드: 형식·원본 30MB 확인 → `createImageBitmap(file, {imageOrientation:'from-image'})`로 EXIF 방향 적용 → 긴 변 2048px·WebP 0.8(10MB 초과 시 0.75, 0.7) → presign → XHR PUT·진행률·abort → complete → `PENDING`/`PROCESSING` 1.5초 폴링(최대 60초) → READY면 배치 URL.
- 취소는 PUT abort 후 `DELETE /images/{id}`. 삭제 실패 시 로컬 카드만 제거하고 정리 job에 맡긴다. 동시 업로드 3개, 실패는 새 presign으로 재시도한다.
- 갤러리는 `READY`만 썸네일, `UPLOADING`·`PENDING`·`PROCESSING`은 진행 카드, `FAILED`는 실패 카드다. 대표는 “대표” 배지·테두리로 표시하고 삭제는 확인 대화상자를 거친다. 크롭은 4:3 기본, 원본 비율 선택, 결과도 WebP·2048px 제한이다.
- 키보드 화살표/Enter/Delete, 크롭 포커스 트랩·ESC·포커스 복귀, 대체 텍스트 `"{물품명} 사진 {순번}"`, 장식 이미지 `alt=""`. 진행률은 `role="progressbar"`, 상태 변화는 `aria-live="polite"`, 터치 44px, 포커스 2.5px, `prefers-reduced-motion` 대응.
- 권한은 `effectivePermission`, `isOwner`로 결정한다. `VIEW`는 갤러리·다운로드만, `EDIT`/`MANAGE`는 Inventory 업로드·대표·삭제, `OWNER`는 Item까지다. 배치 URL은 보이는 `READY` ID를 중복 제거해 100개 단위로 요청하고 `['image-urls', variant, sortedIds]`로 캐시한다(`expiresAt-60초` staleTime). 없는 URL은 줄무늬 플레이스홀더와 “불러오지 못함”으로 표시한다.
- 오류 매핑: `IMAGE_TOO_LARGE` “10MB 이하로 줄여 주세요”, `IMAGE_FORMAT_UNSUPPORTED` “WebP로 변환할 수 없는 파일입니다”, `IMAGE_LIMIT_EXCEEDED` “사진은 최대 10장입니다”, `IMAGE_UPLOAD_EXPIRED` “업로드 시간이 만료되었습니다”, `IMAGE_STORAGE_UNAVAILABLE` “잠시 후 다시 시도해 주세요”. `401` 로그인, `403` 권한, `404` 삭제 안내, `429` 재시도.

## 8. 순서가 있는 작업 분해

| # | 작업 | 주요 파일 | 검증 |
|---|---|---|---|
| 0 | M3·M4 병합, 마이그레이션 번호, MinIO CORS, `primaryImageId` 확인 | 체크리스트 | `git log`, `grep primaryImageId`, `docker compose config` |
| 1 | 의존성·설정 | `gradle/libs.versions.toml`, `backend/build.gradle.kts`, `application*.yml`, `AppProperties.kt`, `MediaProperties.kt` | `./gradlew :backend:compileKotlin`, `MediaPropertiesTest` |
| 2 | 이미지·Outbox 마이그레이션 | `V5__image.sql`, `V6__outbox_event.sql` | `ImageSchemaTest`, `bash scripts/docs_generate.sh schema` |
| 3 | 키·S3·health·CORS | `ImageObjectKey.kt`, `ImageStoragePort.kt`, `S3ImageStorageAdapter.kt`, `S3HealthIndicator.kt`, `infra/minio/cors.json`, `infra/minio/init.sh`, `infra/docker-compose.yml` | `ImageObjectKeyTest`, `S3ImageStorageAdapterIT`, `S3HealthIndicatorTest`, `MinioCorsIT` |
| 4 | presign | `ImagePresignService.kt`, `ImageController.kt`, `ImageDtos.kt`, `ImageMapper.kt`/`.xml` | `ImagePresignServiceTest`, `ImagePresignIT` |
| 5 | complete | `ImageCompleteService.kt` | `ImageCompleteServiceTest`, `ImageMagicBytesTest` |
| 6 | Outbox·토폴로지·confirm | `OutboxEvent.kt`, `OutboxMapper`, `OutboxRelay.kt`, `RabbitTopologyConfig.kt`, `PublisherConfirmConfig.kt` | `OutboxRelayTest`, `RabbitTopologyTest` |
| 7 | worker·processor | `ImageProcessConsumer.kt`, `ImageProcessService.kt`, `WebpImageProcessor.kt`, `ImageProcessorPort.kt` | `WebpImageProcessorTest`, `ImageProcessConsumerTest` |
| 8 | 대표·쿼터·삭제·DRAFT 연결 | `ImagePrimaryService.kt`, `ImageDeleteService.kt`, `ImageAttachmentService.kt`, `ImageQuotaService.kt` | `ImagePrimaryServiceTest`, `ImageQuotaServiceTest`, `ImagePermissionTest` |
| 9 | 만료·purge·stuck·Outbox 정리 | `ImageExpiryJob.kt`, `ImagePurgeJob.kt`, `ImageStuckJob.kt`, `OutboxCleanupJob.kt` | `ImageCleanupJobTest`, `OutboxCleanupJobTest` |
| 10 | 계약·이벤트·데이터 사전 | `docs/generated/*`, `frontend/src/shared/api/schema.d.ts` | `bash scripts/docs_generate.sh all`, `pnpm generate:api`, `python3 -B scripts/docs_check.py` |
| 11 | 프론트 핵심·화면 연결 | `frontend/src/features/media/**`, `inventory-*-page.tsx`, `item-*-page.tsx`, `router.tsx` | `pnpm lint`, `pnpm test`, `pnpm build`, RTL 통합 |
| 12 | 관측·운영·전체 검증 | `scripts/dlq_replay.py`, 메트릭·로그 코드, `docs/operations.md` | `./gradlew :backend:ktlintCheck :backend:detekt :backend:test`, `pnpm exec playwright test`, `docs_check` |

- Step 4~5로 API를 닫고 Step 6~7에서 worker를 붙인다. Step 8~9는 상태 전이가 안정된 뒤 진행한다. 각 Step에서 새 테스트가 실패하면 다음으로 넘어가지 않는다. 프론트 타입은 Step 10 후 `pnpm generate:api`로만 만든다.

## 9. 테스트

| 백엔드 테스트 | 확인 |
|---|---|
| `ImageSchemaTest` / `ImageObjectKeyTest` | 상태·대상·READY·DRAFT CHECK, partial unique, 인덱스, 키·128 유도·`..`·타 Space 거부 |
| `ImageMagicBytesTest` / `WebpImageProcessorTest` | RIFF/WEBP, JPEG·PNG 위장, 잘린 파일, 0바이트·10MB, 재인코딩·2048px·512/128·알파·EXIF 제거·SHA-256 |
| `ImagePresignServiceTest` / `ImageCompleteServiceTest` | `UPLOADING` 행·TTL·권한·10장·DRAFT 20장·Idempotency-Key, 객체 없음·키 불일치·크기·만료·중복·PENDING/DRAFT·Outbox |
| `ImageQuotaServiceTest` / `ImagePrimaryServiceTest` | 동시 11번째 거부, 삭제 후 재업로드, 첫 READY·수동·삭제·실패 승격, unique 경합 |
| `ImagePermissionTest` | Item OWNER, Inventory EDIT, VIEW, 철회 후 404, DRAFT, 교차 Space |
| `ImageProcessConsumerTest` / `OutboxRelayTest` | 정상·중복 skip·삭제 중 처리·FAILED 재시도·DLQ, lease·ack/nack·timeout·backoff·max FAILED |
| `ImageCleanupJobTest` / `OutboxCleanupJobTest` | UPLOADING/DRAFT/FAILED 만료, DELETED 30일, S3 404·오류, PUBLISHED 7일·FAILED 30일 |
| `S3HealthIndicatorTest` / `S3ImageStorageAdapterIT` / `MinioCorsIT` / `ImageControllerIT` | UP/DOWN, presign PUT/GET/head/delete, OPTIONS, 8개 경로·problem+json·CSRF·권한 |

| 프론트 테스트 | 확인 |
|---|---|
| `convert-to-webp.test.ts` | 2048px, 0.8, 10MB 품질 하향, HEIC 오류 |
| `use-image-upload.test.tsx` / `image-uploader.test.tsx` | presign→PUT→complete, 진행률, 취소 DELETE, 만료, 중복 제출, `aria-live` |
| `image-gallery.test.tsx` / `use-image-urls.test.tsx` | READY만, 대표 배지·변경, 삭제 확인, 403 숨김, 100개 청크·중복 제거·캐시 |
| `image-crop-dialog.test.tsx` / `image-upload.spec.ts` | 4:3·키보드·포커스, Playwright 업로드·대표·삭제 |

- Testcontainers는 기존 PostgreSQL·RabbitMQ에 MinIO `GenericContainer`를 추가하고 `TestcontainersConfiguration.kt`에 둔다. 픽스처는 `backend/src/test/resources/images/`의 `valid-1x1.webp`, `valid-exif.webp`, `not-webp.jpg`, `truncated.webp`, `oversize.bin`이다. 시간 의존 테스트는 `Clock`을 고정한다.
- 공유 시나리오는 `@Tag("sharing")`으로 분리하고 M4 병합 후 활성화한다. Playwright는 presign/complete/urls를 인터셉트하고 S3 PUT은 테스트 URL로 성공시킨다. 실제 MinIO E2E는 M8이다.

## 10. 계약·문서·CI·관측·운영

| 대상 | 작업 |
|---|---|
| `docs/generated/openapi.yaml` | `uploadExpiresAt`, `THUMB_SMALL`, `unavailableImageIds`, 새 오류 응답. operationId 8개 유지 |
| `docs/generated/events.yaml` | `image.deleted` 채널, `image.uploaded`/`image.processed` payload 확장 |
| `docs/generated/data-dictionary.md` | 3번 항목의 컬럼·제약·인덱스 |
| `frontend/src/shared/api/schema.d.ts` | `pnpm generate:api` |
| `docs/contracts.md` / `docs/operations.md` | 오류 코드 링크, `agolock.media.*`·`agolock.outbox.*`, 정리 job·health 갱신 |
| `docs/media-ai.md` / `docs/index.yaml` | 128 채택 시 3.3 갱신, 승인 후 `PLAN_006_MEDIA_MESSAGING` 등록(현재는 변경 금지) |

- generated 문서는 생성기 전환이 끝났으면 직접 수정하지 않는다. 아직이면 [ADR-014](../decisions/ADR-014-generated-docs-draft.md)에 따라 초안을 수동 갱신한다.
- CI: `backend` `./gradlew :backend:ktlintCheck :backend:detekt :backend:test`+Testcontainers MinIO, `frontend` `pnpm generate:api`·`pnpm lint`·`pnpm test`·`pnpm build`·`pnpm exec playwright test`, `docs` `python3 -B scripts/docs_check.py`, `contract` `bash scripts/docs_generate.sh all`+타입 diff. WebP native 로드는 `WebpImageProcessorTest`로 확인한다.
- 메트릭: `agolock_image_presign_total`, `complete_total`, `process_seconds{result}`, `process_failures_total{reason}`, `cleanup_deleted_total{reason}`, `outbox_pending`, `outbox_publish_failures_total`, `image_dlq_depth`. 로그는 `request_id`, `space_id`, `user_id`, `image_id`를 포함하고 presigned URL은 제외한다.
- 알림: DLQ 1건 이상 5분, Outbox PENDING 100건 이상 10분, 이미지 실패율 5% 이상 10분, S3 readiness DOWN, 정리 job 연속 실패. 운영 CLI는 `scripts/dlq_replay.py --queue image.process.dlq --limit 10 [--dry-run]`이며 공개 API로 노출하지 않는다.
- runbook: DLQ 재처리, `PROCESSING` 30분 초과, Outbox `FAILED` 복구, S3 CORS·버킷 권한 오류, 고아 객체 확인. S3 장애 시 이미지 API는 `503 IMAGE_STORAGE_UNAVAILABLE`, CRUD는 계속 동작한다.

## 11. 완료 체크리스트

- [ ] `image` 7개 상태와 전이 가드가 마이그레이션·서비스·테스트에 구현되었다.
- [ ] presign이 `UPLOADING` 행과 5분 URL을 만들고 complete가 객체·매직 바이트·크기·해시를 검증한다.
- [ ] Item은 `OWNER`, Inventory는 `EDIT` 이상만 수정하고 VIEW는 조회·다운로드만 가능하다.
- [ ] 단건·배치 URL이 권한 확인 후 발급되고 목록에는 URL이 포함되지 않는다.
- [ ] 대표 자동 승격·수동 변경·삭제·실패 후 승격이 테스트된다.
- [ ] 대상별 10장, DRAFT 20장 제한이 동시 요청에서도 지켜진다.
- [ ] 비공개 버킷, CORS, path-style, public endpoint가 로컬에서 동작한다.
- [ ] worker가 재인코딩·EXIF 제거·512/128·SHA-256을 수행하고 READY에서만 노출한다.
- [ ] `image.uploaded` Outbox가 complete와 같은 트랜잭션에 저장되고 `image.process`가 소비한다.
- [ ] publisher confirm, 재시도, `FAILED`, DLQ, lease 재기동이 테스트된다.
- [ ] S3 health가 readiness에 포함되고 상세 오류를 노출하지 않는다.
- [ ] UPLOADING·DRAFT·FAILED 만료, DELETED 30일 purge, stuck job이 동작한다.
- [ ] 갤러리·업로드·대표·삭제·진행·취소·만료·오류 UI가 권한별로 동작한다.
- [ ] OpenAPI·이벤트·데이터 사전·`schema.d.ts`가 재생성되고 `docs_check`가 통과한다.
- [ ] `./gradlew :backend:ktlintCheck :backend:detekt :backend:test`와 `pnpm lint`, `pnpm test`, `pnpm build`, `pnpm exec playwright test`가 통과한다.
- [ ] [구현 로드맵](../implementation-roadmap.md) M5 완료 조건과 일치한다.

## 12. 위험, 미결정, 후속

| 위험 | 대응 |
|---|---|
| WebP native ARM64/CI 미지원 | 후보 2종을 CI에서 먼저 검증, 실패 시 x86_64 고정 |
| MinIO CORS 미설정·서명 호스트 불일치 | `MinioCorsIT`·`OPTIONS`, `public-endpoint` 명시, staging 실제 PUT 확인 |
| Outbox 중복·쿼터·대표 경합 | lease+상태 멱등성, 부모 행 `FOR UPDATE`, unique 위반 no-op |
| S3 readiness가 전체 API 제거 | 운영 정책 확인, 필요 시 M8에서 `storage` 그룹 분리 |
| 만료 정리와 DRAFT 연결 경합 | 행 잠금·`upload_expires_at` 재확인, `PENDING` 전이 |
| presigned URL 로그 유출 | `LogMasker` 확장, `LogMaskingTest`, `no-store` |
| 마이그레이션 번호 충돌 | M4와 번호 예약, 적용 후 수정 금지 |

| 미결정 | 권장안 |
|---|---|
| 썸네일 변형(확인 필요) | 512(`THUMB`)와 128(`THUMB_SMALL`)을 생성하고 [미디어·AI](../media-ai.md) 3.3을 갱신. 성능 이슈가 없으면 512만 유지 |
| WebP 코덱(확인 필요) | `com.github.usefulness:webp-imageio` 우선, ARM64·Java 21 실패 시 `org.sejda.imageio:webp-imageio` |
| 원본 키 덮어쓰기(확인 필요) | 같은 키에 원자적 덮어쓰기. 원본 보존 필요 시 `source_key`와 ADR |
| 배치 URL 접근 불가 ID(확인 필요) | `unavailableImageIds` additive, 전체 요청 200 |
| worker 배포 형태(확인 필요) | 같은 이미지 `worker` 프로필·웹 비활성, 프로세스 감독·큐 메트릭 |
| `ai_job.image_id` FK와 물리 삭제(확인 필요) | M7에서 `ON DELETE SET NULL`/nullable 검토, M5는 만료 행 물리 삭제 |
| `THUMB_SMALL` 공개·크롭 비율(확인 필요) | M6 성능 측정 후 enum 추가, 크롭은 4:3 기본·원본 비율 선택 |
| S3 SSE(확인 필요) | M8에서 SSE-S3와 presign 헤더 서명 검토 |

- ADR-017 “이미지 후처리 파이프라인과 객체 키”: WebP 코덱, 512/128, 원본 키 덮어쓰기, worker 프로필, S3 CORS·public endpoint.
- ADR-018 “이미지 보관과 삭제 참조 정책”: M7 `ai_job` FK, DRAFT 만료, 30일 purge. M7 착수 전 작성.
- M6 검색은 `primaryImageId`, `hasImage`, `image.processed`를 사용하고 목록에 presigned URL을 넣지 않는다. M7 AI는 DRAFT와 `ImageAttachmentService`를 재사용하고 READY 해시를 캐시 키로 쓴다. M8은 실제 MinIO E2E, SSE, 접근성·성능·백업을 다룬다.
