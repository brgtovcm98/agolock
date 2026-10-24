# PLAN-008: M7 AI 인식과 이미지 검색 상세 구현 계획

> 보관 문서 (2026-10-24): 작성 당시의 설계·제안·구현 및 검증 기록입니다. 본문의 상태·체크리스트·작업 순서·승인 조건은 당시 기록이며 현재 개발 지침이 아닙니다. 미구현 제안도 포함하므로 보관 전환은 구현 완료를 뜻하지 않습니다. 현재 할 일은 [TODO](../todo.md), 동작과 요구사항은 [제품 사양](../product-spec.md) 및 기능별 문서를 확인합니다.

> 상태: 계획 (구현 전)
> 역할: M7의 AI job, DRAFT 연결, 임베딩, 동기 이미지 검색, AI 확인 UI 실행 계획
> 상위 문서: [구현 로드맵](../implementation-roadmap.md)
> 관련 문서: [미디어·AI](../media-ai.md), [검색](../search.md), [계약](../contracts.md), [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md), [운영](../operations.md), [데이터 사전](../generated/data-dictionary.md), [OpenAPI](../generated/openapi.yaml), [이벤트](../generated/events.yaml), [ADR-005](../decisions/ADR-005-rabbitmq-outbox.md), [ADR-007](../decisions/ADR-007-embedding-model.md), [ADR-009](../decisions/ADR-009-ai-confirmation.md), [ADR-011](../decisions/ADR-011-image-upload-lifecycle.md), [ADR-012](../decisions/ADR-012-embedding-versioning.md), [ADR-014](../decisions/ADR-014-generated-docs-draft.md)
> 단일 원본: 요구사항은 [제품 사양](../product-spec.md), 규칙은 [미디어·AI](../media-ai.md)와 [검색](../search.md), 스키마는 [데이터 사전](../generated/data-dictionary.md), API·이벤트는 generated 계약을 따릅니다. 이 문서는 실행 순서와 검증만 정의합니다.

## 1. 목적과 범위

M6의 키워드 검색 위에 2차 MVP의 AI 인식과 이미지 유사도 검색을 얹습니다. 목표는 세 가지입니다.

- AI 실패·지연·취소가 재고 저장 흐름을 막지 않습니다.
- 모델·버전이 일치하는 벡터만 검색에 사용하고, 모델 전환 절차를 재현 가능하게 만듭니다.
- Space 단위 캐시·비용·품질을 기록해 2차 출시 후 비용을 통제합니다.

| 포함 | 제외 |
|---|---|
| `ai_job` 스키마, 인식 요청·조회·취소·재시도 API | 자체 호스팅 CLIP/SigLIP, 텍스트 임베딩·하이브리드 랭킹 |
| `DRAFT` 이미지의 저장 시 연결과 저장 트랜잭션 | 사진별(여러 장) 임베딩, 3차 범위 |
| 인식 결과 스키마, 폼 자동 채움, 카테고리 제안 | 카테고리 자동 생성, 자동 저장 |
| Gemini 멀티모달 임베딩, `item.embedding`, `inventory.effective_embedding` | 검색 이미지 원본 저장, 인앱 알림 |
| 재임베딩 fan-out, 모델·버전 필터, 백필·인덱스 교체 절차 | 실제 운영 모델 전환 실행(도구와 절차만 제공) |
| 동기 이미지 검색 5초 타임아웃, ACL, 후보 overfetch | 다국어 인식(한국어만), provider 자동 벤치마크 |
| Space 캐시·비용·품질, provider fake, 비용 한도, AI 확인 UI | AI 결과 이메일, 3차 알림 센터 |

- AI 결과는 제안이며 사용자 확인 없이 저장하지 않습니다([ADR-009](../decisions/ADR-009-ai-confirmation.md)).
- 임베딩 provider 장애 시 모델 간 fallback을 금지합니다([ADR-012](../decisions/ADR-012-embedding-versioning.md)).
- 3차 기능의 스키마를 미리 만들지 않습니다.

## 2. 선행 조건 M0~M6

| 마일스톤 | M7이 재사용하는 것 | 착수 확인 |
|---|---|---|
| M0 (완료) | 빌드, Testcontainers, Outbox 골격, 계약 생성기, 문서 검사, 디자인 토큰 | `./gradlew test`, `python3 scripts/docs_check.py` 통과 |
| M1 | 세션·CSRF, `app_user`, `space`, `AccessService`, 레이트리밋 | 인증·CSRF 통합 테스트 |
| M2 | Room/Shelf/Box, `effectivePermission`, `isShared` | 계층 권한 테스트 |
| M3 | `item`, `inventory`, `version`, `idempotency_record`, 오류 코드 체계 | `VERSION_CONFLICT`·멱등 재생 테스트 |
| M4 | `share`, Grant 상속, ACL 조회 조건 | 교차 Space·철회 차단 테스트 |
| M5 | `image` 상태 기계, presign/complete, `DRAFT`, 후처리 worker, `image.processed` | `DRAFT`·`READY` 전이와 정리 job 테스트 |
| M6 | 검색 Item 그룹핑, `SearchResponse`, 커서, 제외 조건 | 그룹당 5개·`DISPOSED` 제외 테스트 |

```bash
./gradlew :backend:test
pnpm lint && pnpm test && pnpm build
python3 scripts/docs_impact.py && python3 scripts/docs_check.py
```

- 선행 마일스톤이 하나라도 미완료면 M7을 시작하지 않습니다.
- M5의 `DRAFT` 만료 정리와 `image.processed`, M6의 검색 응답 스키마가 없으면 3·4번 항목을 구현할 수 없습니다.

## 3. 데이터 모델과 마이그레이션

스키마의 단일 원본은 [데이터 사전](../generated/data-dictionary.md)입니다. 여기서는 변경 항목과 이유만 정의하고 DDL을 복제하지 않습니다.

### 3.1 마이그레이션

| 파일 (예시) | 내용 |
|---|---|
| `V7__ai_job.sql` | `ai_job`, `ai_job_type`, `ai_job_status`, 인덱스. 번호는 착수 시 최신 다음 번호로 확정 |
| `V8__embedding_registry.sql` | `embedding_model_registry`, `item.embedding_updated_at`, HNSW 인덱스 정리 |
| `V9__ai_cache_usage.sql` | `ai_cache_entry`, `ai_usage_daily` |
| `V10__ai_job_ops.sql` | `recognition_mode`, `prompt_version`, `cancel_requested_at`, `applied_at`, `applied_inventory_id`, `batch_id` (nullable만 추가) |

- expand-contract로 나누고 M7에서 컬럼 삭제·타입 변경을 하지 않습니다.
- `ai_job` 초안의 `CHECK (cache_key IS NULL OR status='COMPLETED')`는 캐시 조회 시점과 충돌하므로 3.2의 캐시 테이블로 분리하고 `cache_key`는 완료 표식으로만 남깁니다.
- `ai_job.image_id` FK는 `ON DELETE CASCADE`를 권장합니다. 만료 `DRAFT` 정리 job이 이미지를 물리 삭제할 때 참조 job도 정리되어야 삭제가 막히지 않습니다.

### 3.2 테이블 요약

| 테이블 | 목적 | 주요 컬럼·제약 |
|---|---|---|
| `ai_job` | job 상태·결과 | 초안 컬럼 + 3.1의 추가 컬럼. `UNIQUE (idempotency_key)`, `(space_id, status)`, `(status, created_at)`, `(image_id)`, `(space_id, dedupe_key)` 부분 유니크(`PENDING`/`PROCESSING`/`COMPLETED`) |
| `ai_cache_entry` | Space 단위 결과 캐시 | `cache_key` PK(해시), `space_id`, `job_type`, `model`, `mode`, `category_set_hash`, `result`, `created_at`, `expires_at`, `hit_count`; `UNIQUE (space_id, cache_key)`, `(space_id, expires_at)` |
| `ai_usage_daily` | 비용·품질 집계 | PK `(space_id, usage_date, job_type)`; `calls`, `provider_calls`, `cache_hits`, `failures`, `cancellations`, `accepted_count`, `edited_count`, `input_tokens`, `output_tokens`, `cost_amount numeric(12,6)`, `cost_currency`, `latency_ms_sum`, `latency_ms_max` |
| `embedding_model_registry` | 모델 전환 상태 | `model`, `version`, `dimensions`, `status`(`ACTIVE`/`BACKFILL`/`RETIRED`), `activated_at`, `retired_at`; `UNIQUE (model, version)`, `ACTIVE` 1건 부분 유니크 |

- 품질은 저장 시 `appliedJobId`를 받아 서버가 제안값과 최종값을 비교해 수락·수정 건수만 기록합니다. 제안 텍스트를 복제하지 않습니다.
- registry는 실행 설정을 대체하지 않고 전환 상태와 감사 근거로만 사용합니다.

### 3.3 임베딩 컬럼과 차원

| 위치 | 컬럼 | 의미 |
|---|---|---|
| `item` | `embedding`, `embedding_model`, `embedding_version`, `embedding_updated_at` (`vector(1408)`, NULL 허용) | Item 대표 사진 벡터 |
| `inventory` | `effective_embedding` (`vector(1408)`), `embedding_source`(`MASTER`/`INVENTORY`), `embedding_model`, `embedding_version`, `embedding_updated_at` | 검색에 쓰는 유효 벡터 |
| `image` | `embedding`, `embedding_model`, `embedding_version` | 이미지별 원본 벡터 |

- 1차 차원은 1408로 고정합니다([ADR-012](../decisions/ADR-012-embedding-versioning.md)). provider가 다른 차원을 반환하면 저장하지 않고 `EMBEDDING_DIMENSION_MISMATCH`로 실패합니다.
- 벡터와 모델·버전은 항상 같은 트랜잭션에서 갱신합니다. 한 컬럼만 바꾸는 경로를 만들지 않습니다.

### 3.4 HNSW 인덱스

| 인덱스 | 테이블 | 정의·비고 |
|---|---|---|
| `idx_inventory_effective_embedding` | `inventory` | `effective_embedding vector_cosine_ops`, M7에서 유지·`CONCURRENTLY` 재생성 |
| `idx_item_embedding` | `item` | `embedding vector_cosine_ops`, 백필 검증용. 검색 경로에는 사용하지 않음 |

- HNSW에 `space_id`를 선두에 둘 수 없으므로 후보 overfetch와 ACL 필터로 보완합니다([운영](../operations.md) 3.1번 항목).
- 모델·버전 필터는 SQL 조건으로 적용하고 후보를 `limit * 5`(최대 200)로 가져옵니다.
- 같은 차원의 모델 교체는 백필 후 `REINDEX INDEX CONCURRENTLY`로 재구성하고, 차원이 다르면 4.9의 새 컬럼 경로를 사용합니다.

### 3.5 DRAFT 수명주기

| 단계 | 상태 | 트리거 | 만료·정리 |
|---|---|---|---|
| 1 | `UPLOADING` | `POST /images/presign` (`target=DRAFT`) | 24시간 |
| 2 | `DRAFT` | `POST /images/{imageId}/complete` 성공 | 24시간, 물리 삭제 |
| 3 | `DRAFT` | 인식 job 생성·폴링 | job은 이미지 상태를 바꾸지 않음 |
| 4 | `PENDING` | 저장 시 `imageId` 연결(같은 트랜잭션) | 정리 대상 제외, `READY`는 후처리 결과 |
| 5 | `READY` | M5 후처리 완료 | 임베딩 job 등록 |

- `DRAFT` 연결은 신규 Item·Inventory 생성(OWNER)에서만 허용합니다. 공유 사용자는 이미 연결된 `READY` 이미지만 AI 입력으로 사용합니다.
- 저장 시 연결과 `image.uploaded` outbox를 같은 트랜잭션에 넣습니다([미디어·AI](../media-ai.md) 3.4번 항목). 분석 완료 전 저장해도 job을 자동 취소하지 않습니다.

## 4. AI 파이프라인 설계

### 4.1 모듈과 포트

| 구성 | 경로 (예시) | 책임 |
|---|---|---|
| API | `.../ai/api/AiRecognitionController.kt`, `AiJobController.kt` | 검증, 권한, 생성·조회 |
| 서비스 | `.../ai/application/AiJobService.kt`, `RecognitionService.kt`, `EmbeddingService.kt` | 상태 전이, 캐시, 한도 |
| 포트 | `.../ai/provider/RecognitionProvider.kt`, `EmbeddingProvider.kt` | 외부 호출 추상화 |
| 어댑터 | `.../ai/provider/gemini/Gemini*Adapter.kt`, `.../openai/OpenAiRecognitionAdapter.kt` | 요청·응답 매핑, 비용 계산 |
| fake | `.../ai/provider/fake/Fake*Provider.kt` | 결정적 결과, 비용 0, 테스트·로컬 전용 |
| 소비자 | `.../ai/consumer/RecognitionConsumer.kt`, `EmbeddingConsumer.kt`, `AiDlqListener.kt` | 멱등 처리, 재시도, DLQ |
| 운영 | `.../ai/ops/EmbeddingBackfillRunner.kt`, `AiDlqRedriveRunner.kt` | 백필·재처리 CLI |

- 인식과 임베딩 포트를 분리합니다. 인식만 fallback을 허용하고 임베딩은 같은 모델로만 재시도합니다.
- provider 응답 원문은 로그·DB에 남기지 않고 파싱 결과, 토큰, 지연, 비용만 저장합니다.

### 4.2 설정 키

| 키 | 기본값 | 설명 |
|---|---|---|
| `ai.enabled` | `true` | kill switch. `false`면 503 `AI_DISABLED` |
| `ai.recognition.provider` / `model` | `gemini` / 착수 시 고정 | `gemini`/`openai`/`fake` |
| `ai.recognition.fallback-provider` | `openai` | 인식 실패 시 1회. 비활성 가능 |
| `ai.recognition.prompt-version` / `timeout` | `recognition-v1` / 20초 | 프롬프트·스키마 버전, 1회 제한 |
| `ai.recognition.daily-limit-per-user` | 50 | 사용자당 일일 인식 호출 |
| `ai.embedding.provider` / `model` / `version` | `gemini` / 착수 시 고정 / `2024-11` | 활성 모델 |
| `ai.embedding.dimensions` / `timeout` | 1408 / 10초 | 차원 고정, 1회 제한 |
| `ai.job.recognition-max-attempts` / `embedding-max-attempts` | 3 / 5 | 초과 시 DLQ |
| `ai.cache.ttl` | 30일 | 인식 캐시 TTL |
| `ai.cost.space-daily-limit` / `space-monthly-limit` | 5 / 50 USD 상당 | Space 한도 |
| `search.image.timeout` | 5초 | 동기 검색 전체 예산 |
| `search.candidate-multiplier` / `candidate-max` | 5 / 200 | 후보 overfetch |

- 모델명·가격표는 벤치마크 후 `AiProperties`에 모읍니다. 비용 통화는 provider 청구 통화(USD)로 저장하고 KRW 변환은 M8로 미룹니다.

### 4.3 인식 프롬프트와 결과 스키마

- 입력은 대표 이미지 1장, 카테고리 목록(최대 200개, `id`·`name`만), 언어 `ko`, 모드입니다. 이미지 설명·태그는 보내지 않습니다.
- 프롬프트 `recognition-v1`은 "가격을 추정하지 않는다, 카테고리를 새로 만들지 않는다, 허용 필드만 JSON으로 반환한다"를 명시합니다.
- 출력은 `name`, `description`, `categoryCandidate{categoryId?, name, isNew}`, `brand`, `modelName`, `color`, `material`, `confidence`이며 추가 필드를 거부합니다.
- Gemini `responseSchema`, OpenAI `json_schema` 구조화 출력을 사용하고, 스키마 위반은 1회 교정 후 `AI_SCHEMA_INVALID`로 FAILED 처리합니다.
- 카테고리 이름은 길이 제한·이스케이프 후 전달하고 반환 문자열은 trim·최대 길이 검증 후 저장합니다.
- `confidence` 0.4 미만은 자동 채움을 하지 않고, 0.4~0.7은 낮은 신뢰도 배지를 표시합니다.

### 4.4 job 상태 기계

| 전이 | 주체 | 조건·부수 효과 |
|---|---|---|
| `PENDING` → `PROCESSING` | 소비자 | 조건부 UPDATE로 1회 선점, `attempts+1`, `started_at` |
| `PROCESSING` → `COMPLETED` | 소비자 | `cancel_requested_at IS NULL`일 때만 결과·비용 저장, 캐시 write |
| `PROCESSING` → `PENDING` | 소비자 | 일시 오류 후 재전달, `error`에 마지막 코드 |
| `PENDING`/`PROCESSING` → `FAILED` | 소비자/DLQ 리스너 | 한도 초과·설정 오류·최대 시도 초과 |
| `PENDING` → `CANCELLED` | API | 즉시 취소, 이후 이벤트 무시 |
| `PROCESSING` → `CANCELLED` | 소비자 | `cancel_requested_at` 확인 후 결과 폐기 |
| `FAILED`/`CANCELLED` → `PENDING` | API 재시도 | `attempts` 유지, `error` 초기화, outbox 재등록 |

- 모든 전이는 조건부 UPDATE로 수행하고 0건이면 중복 이벤트로 보고 ack합니다. `COMPLETED` job은 취소·재시도할 수 없습니다(409).
- 결과 저장과 캐시 저장은 같은 트랜잭션입니다.

### 4.5 멱등성

- HTTP 생성은 `Idempotency-Key`를 사용하고 M3의 `idempotency_record`로 이전 202 응답을 재생합니다.
- 내부 중복 제거 키는 `dedupe_key = sha256(spaceId, image.sha256, mode, inventoryId, model, promptVersion, categorySetHash)`입니다.
- 같은 `dedupe_key`의 활성 job이 있으면 새로 만들지 않고 기존 job을 202로 반환합니다.
- 캐시가 유효하면 provider 호출 없이 `COMPLETED` job을 만들고 `cache_hit=true`, 비용 0으로 기록합니다.
- 소비자는 `jobId`·`eventId`로 멱등하며, 이벤트 재전달 시 선점 UPDATE가 0건이면 provider를 호출하지 않습니다.

### 4.6 취소와 재시도

- 취소는 `PENDING`이면 즉시 `CANCELLED`, `PROCESSING`이면 `cancel_requested_at`만 기록하고 202를 반환합니다.
- 소비자는 저장 직전 `UPDATE ... WHERE id=? AND status='PROCESSING' AND cancel_requested_at IS NULL`로 확인하고 0건이면 결과를 버립니다.
- 재시도는 `FAILED`·`CANCELLED`에서 허용하고 나머지는 409 `AI_JOB_NOT_RETRYABLE`입니다. 새 job을 만들지 않고 `attempts`를 이어 갑니다.
- 한도 초과 job의 재시도는 429입니다.

### 4.7 DRAFT 연결과 저장 트랜잭션

1. 클라이언트가 `DRAFT` 이미지를 presign·업로드·complete합니다.
2. `POST /ai/recognitions`로 job을 만들고 제안을 폼에 채웁니다.
3. 사용자가 `POST /inventories`에 `imageId`(DRAFT)와 선택적 `appliedJobId`를 보냅니다.
4. 서버는 같은 트랜잭션에서 이미지를 `FOR UPDATE`로 잠그고 `space_id`, `created_by`, `status='DRAFT'`, 만료를 검증합니다.
5. Item·Inventory를 만들고 이미지의 `inventory_id`·`status='PENDING'`을 갱신한 뒤 `image.uploaded` outbox를 기록하고 커밋합니다.
6. `appliedJobId`가 있으면 같은 트랜잭션에서 `applied_at`·`applied_inventory_id`와 품질 지표를 기록합니다.

| 규칙 | 처리 |
|---|---|
| DRAFT가 아님·만료 | 422 `DRAFT_IMAGE_REQUIRED` |
| 다른 Space·다른 사용자 | 404 `RESOURCE_NOT_FOUND` (정보 노출 방지) |
| 대상 이미지 10장 초과 | 422 `IMAGE_LIMIT_EXCEEDED` |
| AI 결과 자동 반영 | 금지. 서버는 요청 본문만 저장 |
| 분석 미완료 상태 저장 | 허용. job은 남고 UI만 폴링 중단 |

- 저장 실패 시 이미지는 `DRAFT`로 남고 24시간 뒤 정리됩니다.
- 공유 Space의 신규 Item은 OWNER만 가능하므로 DRAFT 연결도 본인 Space에서만 성립합니다. 공유 사용자의 재고 사진은 M5의 `INVENTORY` 대상 presign 경로를 사용합니다.

### 4.8 임베딩과 effective_embedding

| 트리거 | job | 결과 |
|---|---|---|
| Inventory 대표 사진 `READY` | `EMBEDDING`, reason `INVENTORY_PRIMARY_READY` | 이미지 벡터 + `inventory.effective_embedding`, `source=INVENTORY` |
| Item 대표 사진 `READY` | `EMBEDDING`, reason `ITEM_PRIMARY_READY` | `item.embedding` + Inventory fan-out |
| Item 대표 사진 교체·삭제 | `EMBEDDING`, reason `MASTER_CHANGED` | 자체 사진이 없는 Inventory에 마스터 벡터 복사 |
| Inventory 대표 사진 삭제 | 동기 처리 | `item.embedding` 복사, `source=MASTER` |
| 모델·버전 변경 | reason `BACKFILL` | 4.9 절차 |

- fan-out은 provider를 다시 호출하지 않고 `item.embedding`을 복사합니다. 비용·지연을 줄이고 같은 사진에 같은 벡터를 보장합니다.
- 복사 전 `embedding_model`·`embedding_version`이 활성 모델과 같은지 확인하고, 다르면 Item 임베딩 job을 먼저 수행합니다.
- `embedding_updated_at`을 비교해 오래된 fan-out 결과를 무시합니다. Inventory 대표 사진이 없으면 `effective_embedding = item.embedding`이어야 하며 이를 깨는 경로를 테스트로 차단합니다.
- provider 장애 시 같은 모델로만 재시도하고, 실패한 Inventory만 이미지 검색에서 제외합니다. 재고 저장은 막지 않습니다.
- `image.processed`를 소비해 `is_primary`인 이미지만 임베딩합니다.

### 4.9 모델 변경 백필과 인덱스 교체

| 단계 | 작업 | 검증 |
|---|---|---|
| 1 | registry에 새 모델을 `BACKFILL`로 등록 | 활성 모델 1건 유지 |
| 2 | 차원이 같으면 기존 컬럼 백필, 다르면 `effective_embedding_v2 vector(N)` 추가 | 컬럼·인덱스 존재 |
| 3 | `EmbeddingBackfillRunner`로 `EMBEDDING` job을 `batch_id` 단위 생성 | 진행률·실패 수, 재실행 멱등 |
| 4 | 새 차원이면 `CREATE INDEX CONCURRENTLY`로 HNSW 생성 | 인덱스 valid, 실행 계획 |
| 5 | `ai.embedding.model/version` 전환 배포, 구 모델 `RETIRED` | 검색 결과에 새 모델만 포함 |
| 6 | 7일 관찰 후 구 인덱스·컬럼 제거 | 롤백 절차 리허설 |

- 전환 중 모델·버전이 다른 벡터를 섞지 않습니다. 전환 전 요청은 구 모델로 완결됩니다.
- 백필 중에는 구 모델로 검색을 유지하거나 `ai.enabled=false`로 비활성화하고 사용자에게 안내합니다.
- 실행 절차는 `backend/src/main/resources/runbooks/embedding-backfill.md`에 두고 완료 후 [운영](../operations.md)에서 링크합니다.

### 4.10 동기 이미지 검색

1. `POST /search/image`가 multipart를 받습니다(최대 10MB, WebP/JPEG/PNG, 매직 바이트 검증).
2. 메모리에서 디코딩·리사이즈(긴 변 2048px) 후 임베딩을 동기 호출합니다. 원본은 저장하지 않습니다.
3. 전체 예산 5초를 강제합니다(provider 4.5초, DB 0.5초).
4. 활성 모델·버전과 일치하고 `effective_embedding IS NOT NULL`인 접근 가능 Inventory만 후보로 조회합니다.
5. HNSW로 `limit*5`(최대 200) 후보를 가져와 ACL·상태·모델을 필터링하고, 부족하면 후보를 2배로 늘리거나 `hnsw.iterative_scan=relaxed_order`를 사용합니다.
6. 결과를 Item 그룹으로 묶어 M6의 `SearchResponse`로 반환합니다(그룹당 5개).

- 타임아웃·provider 오류는 503 `IMAGE_SEARCH_UNAVAILABLE`이며 5초를 넘기지 않습니다.
- 검색 이미지는 캐시하지 않습니다. 같은 Space에 동일 SHA-256 이미지가 있으면 기존 벡터를 재사용할 수 있습니다.
- ACL은 M4·M6의 `AccessService`·검색 조건을 재사용하고 새 경로를 만들지 않습니다.

### 4.11 캐시·비용·품질

| 항목 | 규칙 |
|---|---|
| 캐시 키 | `sha256(spaceId, imageSha256, jobType, model, mode, categorySetHash)` |
| 범위·TTL | Space 경계를 넘지 않음. 인식 30일, 임베딩은 모델 은퇴까지 |
| 저장 대상 | 성공 결과만. 실패·취소·타임아웃은 저장하지 않음 |
| 비용 | provider 호출마다 토큰·지연·비용 기록, 캐시 hit은 0 |
| 한도 | API에서 사용자·Space 한도 선검사(429), 소비자에서 재확인 |
| 품질 | `applied_at`·`edited_count`로 수락률·수정률 집계 |
| kill switch | `ai.enabled=false`면 새 요청 503, 기존 job은 마무리 |

- 캐시 hit은 UI에 "이전 분석 결과"로 표시하고 재분석 버튼을 제공합니다.
- 한도 초과 시 AI 기능만 비활성화하고 재고 CRUD는 유지합니다.

## 5. API 계약

경로·스키마의 단일 원본은 [OpenAPI](../generated/openapi.yaml)입니다.

| Method | Path | 권한 | 요청·응답 | 주요 오류 |
|---|---|---|---|---|
| POST | `/ai/recognitions` | 모드에 따름 | `AiRecognitionRequest` + `Idempotency-Key` → 202 `AiJob` | 400, 403, 404, 422, 429 `AI_QUOTA_EXCEEDED`, 503 `AI_DISABLED` |
| GET | `/ai/jobs/{jobId}` | job 생성자 | → 200 `AiJob` | 404 |
| GET | `/ai/jobs?imageId=` | job 생성자 | → 200 `AiJobList` | 400 |
| POST | `/ai/jobs/{jobId}/retry` | job 생성자 | → 202 `AiJob` | 409 `AI_JOB_NOT_RETRYABLE`, 429 |
| POST | `/ai/jobs/{jobId}/cancel` | job 생성자 | → 202 `AiJob` | 409 `AI_JOB_NOT_CANCELLABLE` |
| POST | `/search/image` | 인증 | multipart + 필터 → 200 `SearchResponse` | 422, 429, 503 `IMAGE_SEARCH_UNAVAILABLE` |
| POST | `/inventories` (확장) | 신규 Item은 OWNER | `imageId`(DRAFT), `appliedJobId` → 201 | 422 `DRAFT_IMAGE_REQUIRED`, `IMAGE_LIMIT_EXCEEDED` |
| GET | `/ai/usage` | OWNER | → 200 `AiUsage` | 403 |

`AiJob` 응답 필드:

| 필드 | 설명 |
|---|---|
| `imageId`, `inventoryId`, `mode` | 대상과 `NEW_ITEM`/`INVENTORY_OVERRIDE` |
| `status`, `attempts`, `errorCode` | 안정적 코드. provider 원문 금지 |
| `cacheHit`, `pollAfterMs` | 캐시 여부, 1000(30초 후 3000) |
| `result.recognition` | `RecognitionResult` 제안 |
| `result.embeddingModel`, `embeddingVersion` | 임베딩 job 결과 |

- `NEW_ITEM`은 OWNER만, `INVENTORY_OVERRIDE`는 Inventory `EDIT` 이상만 허용합니다. job 조회·취소·재시도는 생성자 본인만 가능합니다.
- `GET /ai/jobs`는 새로고침 후 진행 중 job 복구용이며 `imageId` 필터를 필수로 둡니다.
- `GET /ai/usage`는 오늘의 호출·캐시 hit·비용·한도·수락률을 반환합니다.
- 오류 코드 추가: `AI_DISABLED`, `AI_JOB_NOT_RETRYABLE`, `AI_JOB_NOT_CANCELLABLE`, `DRAFT_IMAGE_REQUIRED`, `AI_SCHEMA_INVALID`, `EMBEDDING_DIMENSION_MISMATCH`.
- 503에는 `Retry-After`를 포함합니다. 폴링은 1초 간격으로 시작하고 SSE는 3차로 미룹니다.

## 6. 이벤트

큐·routing key·DLQ의 단일 원본은 [이벤트 계약](../generated/events.yaml)과 [미디어·AI](../media-ai.md) 7.2번 항목입니다.

| 큐 | routing key | payload | 멱등성 |
|---|---|---|---|
| `ai.recognition` | `ai.recognition.requested` | `eventId`, `jobId`, `spaceId`, `imageId`, `inventoryId?`, `mode`, `language`, `model`, `promptVersion`, `categorySetHash` | `jobId` 조건부 UPDATE |
| `ai.embedding` | `ai.embedding.requested` | `eventId`, `jobId`, `spaceId`, `imageId`, `targetType`, `targetId`, `model`, `version`, `reason`, `copyFromItemId?` | `jobId` 조건부 UPDATE |
| `domain.events` | `ai.recognition.completed`, `ai.embedding.completed` | `jobId`, `imageId`, `targetType`, `targetId`, `status`, `cacheHit`, `model`, `version` | `eventId` |

- `reason` 값: `INVENTORY_PRIMARY_READY`, `ITEM_PRIMARY_READY`, `MASTER_CHANGED`, `BACKFILL`, `MANUAL_RETRY`.
- fan-out은 Item 임베딩 완료 트랜잭션에서 Inventory별 outbox를 만듭니다. 500개를 넘으면 `batchId`로 나눕니다.
- 재시도는 5초·30초·2분 백오프를 사용하고 인식 3회, 임베딩 5회를 넘기면 DLQ로 보냅니다.
- DLQ는 `AiDlqRedriveRunner`로 재처리하며 재처리 전 job을 `PENDING`으로 되돌립니다.
- `ai.recognition.failed`, `ai.embedding.failed` 도메인 이벤트 추가를 권장합니다(12번 항목 확인 필요).
- payload에 이미지 바이트, presigned URL, 인식 원문을 넣지 않습니다.

## 7. 프론트엔드

화면·흐름은 [화면과 흐름](../frontend.md), 시각 규칙은 [디자인 시스템](../design-system.md)을 따릅니다.

| 화면 | 경로 (예시) | 역할 |
|---|---|---|
| SCR-011 확장 | `frontend/src/features/inventory/inventory-create-page.tsx` | 사진 입력, AI 분석, 제안 폼, 저장 |
| SCR-010 확장 | `.../inventory/inventory-edit-page.tsx` | 재고 사진 AI 제안 반영 |
| SCR-014 | `.../ai/ai-result-page.tsx` | job 폴링, 제안 검토·수정·저장 |
| SCR-013 | `.../search/image-search-dialog.tsx` | 이미지 검색, 결과, 타임아웃 fallback |
| 공통 | `.../ai/ai-api.ts`, `use-recognition-job.ts`, `recognition-panel.tsx`, `proposal-form.tsx`, `category-suggestion.tsx` | API·상태·표시 |

### 7.1 사진 입력과 분석 흐름

| 단계 | 동작 | 상태 |
|---|---|---|
| 1 | 촬영·파일 선택, WebP 변환(2048px, 0.8) | `idle` |
| 2 | `presign`(`DRAFT`) 후 직접 업로드 | `uploading` (취소 가능) |
| 3 | `complete` 후 `POST /ai/recognitions` | `analyzing` |
| 4 | 1초 폴링, 30초 후 3초 간격, 새로고침 시 `GET /ai/jobs?imageId=` | `analyzing` |
| 5 | `COMPLETED`면 제안 폼, `FAILED`면 재시도 | `completed`/`failed` |
| 6 | 사용자가 수정·확인 후 저장(`imageId`, `appliedJobId`) | `saving` |

- 분석 중에도 모든 입력 필드를 활성화하고, 실패·취소·타임아웃에도 수동 입력과 저장을 유지합니다.
- 제안은 빈 필드만 채우고 사용자 입력을 덮어쓰지 않습니다. 필드별 "적용"과 "전체 적용"을 제공합니다.
- `confidence` 0.4 미만은 자동 채움을 건너뛰고, 0.4~0.7은 낮은 신뢰도 배지를 붙입니다.
- 카테고리는 기존 후보만 보여주고 `isNew`는 OWNER에게만 선택지를 노출합니다. 최종 판정은 서버가 합니다.
- 저장은 기존 흐름과 같고 AI 확인을 별도 단계로 강제하지 않습니다. 확인하지 않으면 제안은 반영되지 않습니다.

### 7.2 이미지 검색과 오류 상태

- 검색 화면의 "이미지로" 칩에서 파일 선택·촬영을 엽니다.
- 남은 시간을 표시하고 5초에 요청을 중단합니다. 클라이언트 `AbortSignal`은 6초입니다.
- 503이면 "이미지 검색을 사용할 수 없습니다"와 "키워드로 검색" 버튼을 표시하고 검색어 입력으로 포커스를 옮깁니다.
- 결과는 기존 마스터 카드·재고 행 UI를 재사용하고 이미지 검색 배지를 붙입니다.
- 429면 대기 시간을 안내하고 재시도를 비활성화합니다.

### 7.3 상태와 접근성

| 항목 | 규칙 |
|---|---|
| 분석 상태 | `aria-live="polite"`로 분석 중·완료·실패 알림 |
| 포커스 | 완료 시 제안 폼 제목, 실패 시 재시도 버튼으로 이동 |
| 버튼 | 취소·재시도는 44px 터치 영역과 키보드 접근, 텍스트 라벨 병기 |
| 신뢰도 | 색만 쓰지 않고 퍼센트 텍스트와 배지 병기 |
| 이미지 | 업로드 사진·결과 썸네일에 대상 이름 기반 `alt` |
| 모션·오류 | `prefers-reduced-motion` 정지, 오류 코드를 문구로 변환하고 수동 입력 경로 유지 |

- 기존 `Badge`, `Card`, `Button`, `StatusTag`와 토큰만 사용하고 새 색·간격을 만들지 않습니다.

## 8. 작업 분해

| 순서 | 작업 | 주요 파일 | 검증 |
|---|---|---|---|
| 1 | 설정·마이그레이션 | `db/migration/V7~V10`, `application.yml`, `AiProperties.kt` | 마이그레이션 테스트, 설정 누락 기동 실패 |
| 2 | job 도메인·저장소·포트 | `ai/domain/AiJob.kt`, `ai/persistence/AiJobMapper.kt`, `mapper/ai/*.xml` | `AiJobStateMachineTest`, `AiJobMapperTest` |
| 3 | 인식 API | `AiRecognitionController.kt`, `AiJobController.kt`, `AiJobService.kt` | `AiRecognitionApiTest`, `AiJobPermissionTest` |
| 4 | 소비자·캐시·비용 | `RecognitionConsumer.kt`, `AiCacheMapper.kt`, `AiUsageMapper.kt`, `AiDlqListener.kt` | `AiRecognitionConsumerTest`, `AiCacheTest`, `AiDlqTest` |
| 5 | DRAFT 연결 | `inventory/application/InventoryService.kt`, `media/application/ImageAttachService.kt` | `DraftImageAttachTest`, `DraftImageExpiryTest` |
| 6 | 임베딩 파이프라인 | `EmbeddingService.kt`, `EmbeddingConsumer.kt`, `GeminiEmbeddingAdapter.kt` | `EmbeddingEffectiveTest`, `EmbeddingFanOutTest`, `EmbeddingNoFallbackTest` |
| 7 | 이미지 검색 | `search/api/ImageSearchController.kt`, `search/application/ImageSearchService.kt` | `ImageSearchAclTest`, `ImageSearchTimeoutTest`, `ImageSearchModelFilterTest` |
| 8 | 백필·재처리 CLI | `ai/ops/EmbeddingBackfillRunner.kt`, `AiDlqRedriveRunner.kt`, runbook | 드라이런·재실행 멱등, runbook 리뷰 |
| 9 | 프론트 AI 흐름 | `features/ai/*`, `inventory-create-page.tsx` | `recognition-panel.test.tsx`, `proposal-form.test.tsx` |
| 10 | 프론트 이미지 검색 | `features/search/image-search-dialog.tsx`, `search-page.tsx` | `image-search-dialog.test.tsx`, Playwright `image-search.spec.ts` |
| 11 | 계약·문서·관측 | `openapi.yaml`, `events.yaml`, `data-dictionary.md`, 메트릭 | `pnpm generate:api`, `python3 scripts/docs_check.py` |

- 각 단계는 백엔드·프론트·테스트를 함께 끝내고 다음으로 넘어갑니다. 5단계는 M5 담당자 리뷰를 받고, 9·10단계는 6·7단계 API 이후에 시작합니다.

## 9. 테스트

| 영역 | 테스트 (예시) | 검증 |
|---|---|---|
| 멱등성 | `AiRecognitionIdempotencyTest` | 같은 키·본문 재생, 다른 본문 409, 같은 dedupe key job 1건 |
| 취소 | `AiJobCancelTest` | `PENDING` 즉시 취소, `PROCESSING` 취소 후 결과 미저장, `COMPLETED` 409 |
| 재시도 | `AiJobRetryTest` | `FAILED`·`CANCELLED` 재시도, `PENDING`·`COMPLETED` 409, 한도 429 |
| DLQ | `AiRecognitionDlqTest`, `AiEmbeddingDlqTest` | 최대 시도 후 DLQ, `FAILED`, 재처리 시 `PENDING` 복귀 |
| 저장 비차단 | `AiFailureDoesNotBlockSaveTest` | 장애·타임아웃·취소 상태에서 `POST /inventories` 201 |
| 무fallback | `EmbeddingNoFallbackTest` | 같은 모델만 재시도, 차원 불일치 실패 |
| 모델·버전 | `ImageSearchModelFilterTest` | 다른 모델·버전 벡터 제외 |
| effective | `EmbeddingEffectiveTest` | 사진 있으면 `INVENTORY`, 삭제 시 `MASTER` 복사 |
| fan-out | `EmbeddingFanOutTest` | provider 재호출 0회, 오래된 결과 무시 |
| 검색 ACL | `ImageSearchAclTest` | 공유 결과 포함, 철회·타 Space·`DISPOSED` 제외 |
| 검색 시간 | `ImageSearchTimeoutTest` | 5초 내 503, 부분 결과 없음, `Retry-After` |
| fake·비용 | `FakeAiProviderCostLimitTest` | 결정적 결과, 사용자·Space 한도 429, 캐시 비용 0 |
| 품질 | `AiQualityRecordTest` | `appliedJobId` 수락·수정 카운트, 제안 미복제 |
| DRAFT | `DraftImageAttachTest` | 같은 트랜잭션 연결, `PENDING`, 만료·타 Space 422/404 |
| UI | `recognition-panel.test.tsx`, `proposal-form.test.tsx` | 분석·취소·재시도·낮은 신뢰도, 입력값 보존 |
| UI 검색 | `image-search-dialog.test.tsx` | 진행률, 타임아웃, 키워드 전환, 429 |
| E2E | `ai-photo-input.spec.ts`, `image-search.spec.ts` | 사진→분석→수정→저장, 검색 실패 fallback |

- 모든 테스트는 `ai.provider=fake`로 실제 네트워크를 호출하지 않습니다.
- Testcontainers PostgreSQL(pgvector)·RabbitMQ로 트랜잭션·재시도·DLQ를 검증하고, 시간은 M0의 `Clock` 주입을 사용합니다.
- 교차 Space 테스트는 M2~M6 픽스처를 재사용합니다.

## 10. 계약·문서·CI·관측

| 대상 | 변경 |
|---|---|
| [OpenAPI](../generated/openapi.yaml) | `AiJob` 필드, `AiJobList`·`AiUsage`, `POST /inventories`의 `imageId`·`appliedJobId`, 오류 코드, `Idempotency-Key` |
| [이벤트](../generated/events.yaml) | 인식·임베딩 payload 확장, `reason`·`targetType`, 완료 전용 스키마, 필요 시 `*.failed` |
| [데이터 사전](../generated/data-dictionary.md) | 신규 3개 테이블, `ai_job` 변경, `item.embedding_updated_at`, 인덱스 |
| [미디어·AI](../media-ai.md)·[운영](../operations.md) | 캐시·비용·fan-out 복사·DRAFT FK 정책, `ai.*` 설정, DLQ 재처리, 비용 알림 |

- generated 문서는 [ADR-014](../decisions/ADR-014-generated-docs-draft.md)에 따라 착수 전까지 수동 초안으로 갱신하고 머리말 표시를 유지합니다.
- 프론트 타입은 `pnpm generate:api`로 재생성하고 결과를 커밋합니다. `docs/index.yaml` 등록은 M7 착수 PR에서 처리합니다.
- CI는 `ai` 패키지 테스트, Testcontainers RabbitMQ, AI·검색 Vitest·Playwright, `generate:api` diff, 비밀 스캔을 포함하고 실제 provider 키를 쓰지 않습니다.
- 메트릭: `ai_job_duration_seconds{job_type,status}`, `ai_provider_failures_total`, `ai_cache_hits_total`, `ai_cost_total`, `image_search_duration_seconds`, `image_search_timeout_total`, `ai_dlq_messages`.
- 로그에는 `job_id`, `space_id`, `provider`, `model`, `latency_ms`, `cost`만 남기고 이미지 바이트·원문 응답·presigned URL을 제외합니다.
- 알림: DLQ 증가, AI 실패율 10% 초과, 일일 비용 80%·100%, 이미지 검색 타임아웃 5% 초과.
- 비용 통제: 캐시 우선, fan-out 복사, 카테고리 200개 제한, 2048px 다운스케일, 사용자·Space 한도, `ai.enabled` kill switch.

## 11. 완료 조건 매핑

| M7 완료 조건 | 증거 |
|---|---|
| AI 실패가 저장을 막지 않음 | `AiFailureDoesNotBlockSaveTest` |
| job 멱등성·취소 후 저장 금지·DLQ 재처리 | `AiRecognitionIdempotencyTest`, `AiJobCancelTest`, `AiRecognitionDlqTest` |
| fallback 없음, 모델·버전 필터 | `EmbeddingNoFallbackTest`, `ImageSearchModelFilterTest` |
| 이미지 검색 ACL·5초 타임아웃 | `ImageSearchAclTest`, `ImageSearchTimeoutTest` |
| `ai_job`과 인식 요청·상태·취소·재시도 | API 통합 테스트, OpenAPI 경로 |
| DRAFT 이미지 저장 시 연결 | `DraftImageAttachTest`, 201 응답의 `PENDING` |
| 인식 스키마·폼 자동 채움 | `ProposalForm` 테스트, `RecognitionResult` 계약 |
| Gemini 임베딩·`item.embedding`·`effective_embedding` | `EmbeddingEffectiveTest`, 마이그레이션 검사 |
| fan-out·백필·인덱스 교체 절차 | `EmbeddingFanOutTest`, runbook 리뷰 |
| Space 캐시·비용·품질, fake·한도 | `AiCacheTest`, `FakeAiProviderCostLimitTest` |
| AI 확인 UI | SCR-014 Vitest·Playwright, 접근성 점검 |
| 계약 재생성 | `bash scripts/docs_generate.sh all`, `python3 scripts/docs_check.py` |

## 12. 위험과 열린 질문

| 위험 | 영향 | 대응 |
|---|---|---|
| provider 지연·장애 | 분석 지연 | 비동기 job, 5초 검색 타임아웃, 인식만 fallback |
| 모델 혼합 | 검색 무효 | 모델·버전 컬럼, 필터, 백필 runbook |
| DRAFT 고아·FK 충돌 | 정리 실패 | FK `ON DELETE CASCADE`, 만료 정리 테스트 |
| 비용 급증 | 운영 비용 | 캐시, 복사 fan-out, 한도, 알림, kill switch |
| 공유 DRAFT 교차 참조 | 권한 우회 | DRAFT는 본인 Space, 공유는 `READY` 이미지만 |
| 잘못된 AI 제안 | 데이터 품질 | 자동 저장 금지, 신뢰도 배지, 사용자 확인 |
| DLQ 적체 | 결과 누락 | DLQ 알림, 재처리 CLI, job 상태 복구 |

| 열린 질문 | 권장안 | 상태 |
|---|---|---|
| `ai_cache_entry`·`ai_usage_daily` 도입, `ai_job.cache_key` 정리 | 도입하고 데이터 사전 갱신 | 확인 필요 |
| `ai.recognition.failed`·`ai.embedding.failed` 이벤트 | 추가해 DLQ·알림과 분리 | 확인 필요 |
| 마스터 fan-out을 provider 재호출 대신 벡터 복사 | 복사 채택, ADR-012 해석 명시 | 확인 필요 |
| `CANCELLED` job 재시도 허용 | 허용하되 새 job 미생성 | 확인 필요 |
| `Idempotency-Key` 필수화 | 인식 생성에 한해 필수 | 확인 필요 |
| `GET /ai/usage` 노출 범위 | OWNER 전용, 숫자만 표시 | 확인 필요 |
| 기본 인식 provider | 30장 벤치마크 후 결정, 설정 교체 가능 | 확인 필요 |
| 이미지 검색 `sort` | `RELEVANCE`만 허용하고 계약에서 제거 | 확인 필요 |
| AI 결과 보관 기간 | 완료 30일, 적용 결과 90일 | 확인 필요 |

- 확정 시 ADR-017(AI job 캐시·비용·한도)과 ADR-018(임베딩 모델 전환·백필 절차)을 추가합니다. ADR-018의 [ADR-012](../decisions/ADR-012-embedding-versioning.md) 대체 여부는 확인이 필요합니다.
- M8은 이 결과를 가지고 E2E, 부하, 접근성, 생성기 완성, OpenAPI 호환성 검사를 수행합니다. 다음 단계는 [구현 로드맵](../implementation-roadmap.md)의 M8을 따릅니다.
