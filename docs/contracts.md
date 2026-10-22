# API와 이벤트 계약

> 상태: 확정
> 역할: HTTP API와 비동기 이벤트의 규칙, 인증, 오류, 페이지네이션, 계약 문서 링크
> 관련 문서: [OpenAPI 계약](generated/openapi.yaml), [이벤트 계약](generated/events.yaml), [아키텍처](architecture.md), [공유](sharing.md), [ADR-014](decisions/ADR-014-generated-docs-draft.md), [ADR-015](decisions/ADR-015-contract-preview-mode.md)

## 1. 기본 규칙

- Base path는 `/api/v1`입니다.
- 요청과 응답은 JSON, UTF-8을 사용합니다.
- 필드 이름은 camelCase를 사용합니다.
- 날짜·시각은 ISO 8601 UTC 문자열을 사용합니다.
- ID는 UUID 문자열을 사용합니다.
- 금액은 정수와 통화 코드로 표현하며 MVP 통화는 KRW입니다.
- 성공 응답은 리소스 또는 `{ "items": [], "nextCursor": null }` 형태를 사용합니다.
- 생성 성공은 `201 Created`와 `Location` 헤더를 사용합니다.
- 삭제 성공은 `204 No Content`를 사용합니다.
- 생성 요청은 `Idempotency-Key`를 지원합니다.
- PATCH는 JSON Merge Patch 의미를 사용합니다. 필드 생략은 유지, `null`은 오버라이드 해제입니다.
- 낙관적 잠금 대상 리소스의 PATCH는 `version`을 필수로 요구합니다.
- 버전이 일치하지 않으면 `409 VERSION_CONFLICT`를 반환합니다.
- `Idempotency-Key`는 사용자별로 24시간 저장합니다.
- 같은 키와 같은 요청은 이전 응답을 재생하고, 다른 요청에 같은 키를 쓰면 `409 IDEMPOTENCY_KEY_REUSED`를 반환합니다.

## 2. 인증과 보안

- 인증은 HttpOnly 세션 쿠키를 사용합니다.
- 세션 쿠키 이름은 `SESSION`입니다.
- 로그인은 `POST /auth/login`입니다.
- 로그인과 회원가입 성공 시 세션 ID를 회전합니다.
- 로그아웃과 비밀번호 재설정 완료 시 해당 사용자의 모든 세션을 무효화합니다.
- 상태 변경 요청은 CSRF 토큰을 함께 보냅니다.
- CSRF 토큰은 `GET /auth/csrf` 또는 `XSRF-TOKEN` 쿠키로 획득합니다.
- CSRF 토큰은 `X-XSRF-TOKEN` 헤더로 전달합니다.
- 세션 만료 시 `401 Unauthorized`를 반환합니다.
- 권한이 없으면 `403 Forbidden`을 반환합니다.
- 접근 권한이 없는 리소스는 정보 노출을 막기 위해 `404 Not Found`로 응답합니다.
- 로그인과 초대코드 검증에는 레이트리밋을 적용합니다.
- presigned URL은 API 응답에만 포함하고 로그에 남기지 않습니다.
- `GET /auth/config`는 가입 화면에 필요한 `inviteRequired`, 비밀번호 정책을 반환합니다.

## 3. 오류 형식

RFC 7807 `application/problem+json`을 사용합니다.

```json
{
  "type": "https://agolock.example/problems/permission-denied",
  "title": "권한이 없습니다",
  "status": 403,
  "detail": "이 재고를 수정할 권한이 없습니다.",
  "instance": "/api/v1/inventories/018f...",
  "code": "PERMISSION_DENIED",
  "fieldErrors": []
}
```

- `code`는 클라이언트 분기용 안정적인 문자열입니다.
- `fieldErrors`는 검증 실패 시 필드별 메시지를 담습니다.
- 내부 예외 메시지와 스택 트레이스를 노출하지 않습니다.

### 3.1 상태 코드 기준

| 상황 | 상태 |
|---|---|
| 문법·타입·필수 필드 오류 | `400 Bad Request` |
| 인증 없음·세션 만료 | `401 Unauthorized` |
| 리소스는 보이지만 작업 권한 없음 | `403 Forbidden` |
| 리소스 없음·접근 범위 밖 | `404 Not Found` |
| 버전 충돌·중복·삭제 차단 | `409 Conflict` |
| 의미·비즈니스 검증 실패 | `422 Unprocessable Entity` |
| 요청 한도 초과 | `429 Too Many Requests` |
| 외부 AI·이미지 검색 일시 장애 | `503 Service Unavailable` |

### 3.2 주요 오류 코드

| 코드 | 의미 |
|---|---|
| `VALIDATION_FAILED` | 요청 값 검증 실패 |
| `PERMISSION_DENIED` | 작업 권한 없음 |
| `RESOURCE_NOT_FOUND` | 리소스 없음 또는 접근 범위 밖 |
| `VERSION_CONFLICT` | 낙관적 잠금 충돌 |
| `IDEMPOTENCY_KEY_REUSED` | 같은 키로 다른 요청 |
| `INVALID_CURSOR` | 잘못된 커서 |
| `CURSOR_NOT_SUPPORTED_FOR_SORT` | 해당 정렬은 커서 페이지네이션을 지원하지 않음 |
| `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 Content-Type |
| `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| `AUTHENTICATION_REQUIRED` | 세션 없음·만료 |
| `CSRF_INVALID` | CSRF 토큰 누락·불일치 |
| `EMAIL_ALREADY_EXISTS` | 활성 이메일 중복 |
| `INVALID_CREDENTIALS` | 이메일 또는 비밀번호 불일치 |
| `RATE_LIMITED` | 레이트리밋 또는 로그인 실패 잠금 |
| `INVITE_CODE_REQUIRED` | 초대코드 누락 |
| `INVITE_CODE_INVALID` | 초대코드 무효·만료·철회·소진 |
| `PASSWORD_RESET_TOKEN_INVALID` | 재설정 토큰 무효·만료·사용됨 |
| `INVITATION_ALREADY_PENDING` | 대기 중인 초대 존재 |
| `SHARE_ALREADY_EXISTS` | 활성 Grant 존재 |
| ``EMAIL_DISCOVERY_DISABLED`` | 이메일 초대 탐색이 비활성화됨 |
| `INVITATION_EXPIRED` | 만료된 초대 |
| `DISPOSAL_CONFIRMATION_REQUIRED` | 폐기 확인 플래그 누락 |
| `CATEGORY_IN_USE` | 사용 중인 카테고리 삭제 시도 |
| `ITEM_HAS_INVENTORIES` | 삭제되지 않은 Inventory가 남은 Item 삭제 시도 |
| `LOCATION_NOT_EMPTY` | 하위 리소스나 재고가 있는 위치 삭제 |
| `IMAGE_NOT_READY` | 처리되지 않은 이미지 사용 |
| `IMAGE_LIMIT_EXCEEDED` | 대상별 이미지 수 초과 |
| `CURRENCY_NOT_SUPPORTED` | KRW 이외 통화 |
| `IMAGE_SEARCH_UNAVAILABLE` | 이미지 검색 임베딩 실패·타임아웃 |
| `AI_QUOTA_EXCEEDED` | AI 호출 한도 초과 |
| `AI_PROVIDER_UNAVAILABLE` | AI provider 사용 불가 |

## 4. 페이지네이션

- 커서 기반 페이지네이션을 사용합니다.
- 요청: `cursor`, `limit`
- 응답: `items`, `nextCursor`
- 기본 `limit`은 20, 최대 100입니다.
- 검색은 Item 그룹 단위로 페이지네이션합니다.
- `UPDATED_DESC` 검색은 `(updated_at, itemId)` keyset 커서를 사용합니다.
- `RELEVANCE` 검색은 첫 페이지만 제공하며 `nextCursor`를 반환하지 않습니다. 커서를 보내면 `400 CURSOR_NOT_SUPPORTED_FOR_SORT`입니다.
- `NAME_ASC`/`VIEWED_DESC`/`STATUS_ASC`는 1차에서 기존 offset 커서를 유지합니다.
- 커서는 정렬 키와 `itemId`를 포함합니다.
- 잘못된 커서는 `400 INVALID_CURSOR`를 반환합니다.
- 전체 개수는 별도 count API가 필요한 경우에만 제공합니다.

## 5. 엔드포인트 목록

### 5.1 인증과 설정

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| GET | `/auth/config` | 가입·로그인 설정 | 공개 |
| GET | `/auth/csrf` | CSRF 토큰 발급 | 공개 |
| POST | `/auth/signup` | 회원가입, Space 생성 | 공개 + 설정 시 초대코드 |
| POST | `/auth/login` | 로그인 | 공개 |
| POST | `/auth/logout` | 로그아웃 | 인증 |
| GET | `/auth/me` | 내 사용자·Space 정보 | 인증 |
| PATCH | `/auth/me` | 표시 이름 수정 | 인증 |
| POST | `/auth/password-reset/request` | 재설정 메일 요청 | 공개 |
| POST | `/auth/password-reset/confirm` | 새 비밀번호 설정 | 공개 |
| PATCH | `/spaces/{spaceId}` | Space 이름 수정 | OWNER |

### 5.2 계층

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| GET | `/rooms` | 내 Room 목록 | 인증 |
| POST | `/rooms` | Room 생성 | OWNER |
| GET | `/shared` | 공유받은 Room/Shelf 목록 | 인증 |
| GET | `/rooms/{roomId}` | Room 상세 | 접근 권한 |
| PATCH | `/rooms/{roomId}` | Room 수정 | OWNER |
| DELETE | `/rooms/{roomId}` | Room 삭제 | OWNER |
| GET | `/rooms/{roomId}/shelves` | Shelf 목록 | 접근 권한 |
| POST | `/rooms/{roomId}/shelves` | Shelf 생성 | OWNER |
| GET | `/shelves/{shelfId}` | Shelf 상세 | 접근 권한 |
| PATCH | `/shelves/{shelfId}` | Shelf 수정 | OWNER |
| PATCH | `/shelves/{shelfId}/parent` | Shelf 이동 | OWNER |
| DELETE | `/shelves/{shelfId}` | Shelf 삭제 | OWNER |
| GET | `/shelves/{shelfId}/boxes` | Box 목록 | 접근 권한 |
| POST | `/shelves/{shelfId}/boxes` | Box 생성 | OWNER |
| GET | `/boxes/{boxId}` | Box 상세 | 접근 권한 |
| PATCH | `/boxes/{boxId}` | Box 수정 | OWNER |
| PATCH | `/boxes/{boxId}/parent` | Box 이동 | OWNER |
| DELETE | `/boxes/{boxId}` | Box 삭제 | OWNER |

### 5.3 물품과 재고

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| GET | `/items` | Item 목록 | 접근 가능 범위 |
| POST | `/items` | Item 생성 | OWNER |
| GET | `/items/{itemId}` | Item 상세 | 접근 가능 범위 |
| PATCH | `/items/{itemId}` | Item 마스터 수정 | OWNER |
| DELETE | `/items/{itemId}` | Item 삭제 | OWNER |
| GET | `/items/{itemId}/inventories` | Item의 Inventory 목록 | 접근 가능 범위 |
| GET | `/items/{itemId}/images` | Item 이미지 목록 | 접근 가능 범위 |
| GET | `/items/{itemId}/update-impact` | 마스터 수정 영향 범위 | OWNER |
| GET | `/inventories` | Inventory 목록 | 접근 가능 범위 |
| POST | `/inventories` | Inventory 생성, 신규 Item 선택 가능 | 기존 Item은 MANAGE, 신규 Item은 OWNER |
| GET | `/inventories/{inventoryId}` | Inventory 상세 | 접근 권한 |
| PATCH | `/inventories/{inventoryId}` | 오버라이드·상태 수정 | EDIT 이상 |
| DELETE | `/inventories/{inventoryId}` | Inventory 삭제 | MANAGE 이상 |
| PATCH | `/inventories/{inventoryId}/location` | 위치 이동 | source/target MANAGE |
| GET | `/inventories/{inventoryId}/move-targets` | 이동 가능한 위치 | source MANAGE |
| GET | `/inventories/{inventoryId}/movements` | 이동 이력 | VIEW 이상 |
| GET | `/inventories/{inventoryId}/images` | Inventory 이미지 목록 | 접근 권한 |

### 5.4 카테고리

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| GET | `/categories` | 접근 가능한 Category 목록 | 접근 가능 범위 |
| POST | `/categories` | Category 생성 | OWNER |
| PATCH | `/categories/{categoryId}` | Category 수정 | OWNER |
| DELETE | `/categories/{categoryId}` | Category 삭제 | OWNER |
| POST | `/categories/{categoryId}/merge` | Category 병합 | OWNER |

### 5.5 검색

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| GET | `/search` | 키워드 검색 | 인증 |
| GET | `/search/recent` | 최근 검색어 | 인증 |
| DELETE | `/search/recent` | 최근 검색어 삭제 | 인증 |
| GET | `/search/frequent` | 자주 본 물건 | 인증 |
| GET | `/search/suggestions` | 추천 토큰 | 인증 |
| POST | `/search/image` | 이미지 검색 | 인증 |

### 5.6 이미지

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| POST | `/images/presign` | 업로드 URL 발급 | ITEM은 OWNER, INVENTORY는 EDIT 이상, DRAFT는 인증 |
| POST | `/images/{imageId}/complete` | 업로드 완료 | presign과 동일 |
| GET | `/images/{imageId}/url` | 다운로드 URL 발급 | 접근 권한 |
| POST | `/images/urls` | 다운로드 URL 일괄 발급 | 접근 권한 |
| POST | `/images/{imageId}/primary` | 대표 이미지 변경 | ITEM은 OWNER, INVENTORY는 EDIT 이상 |
| DELETE | `/images/{imageId}` | 이미지 삭제 | ITEM은 OWNER, INVENTORY는 EDIT 이상 |

- `GET /images/{imageId}/url`은 `variant=THUMB|ORIGINAL`을 지원합니다.
- `POST /images/urls`는 최대 100개 이미지의 URL을 한 번에 발급합니다.
- 목록 응답은 presigned URL 대신 이미지 ID를 반환합니다.

### 5.7 AI

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| POST | `/ai/recognitions` | 인식 job 생성 | 요청 모드에 따름 |
| GET | `/ai/jobs/{jobId}` | job 상태·결과 조회 | job 소유자 |
| POST | `/ai/jobs/{jobId}/retry` | 재시도 | job 소유자 |
| POST | `/ai/jobs/{jobId}/cancel` | 취소 | job 소유자 |

### 5.8 공유

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| GET | `/shares` | Grant 목록 | OWNER |
| PATCH | `/shares/{shareId}` | 권한 변경 | OWNER |
| DELETE | `/shares/{shareId}` | 공유 철회 | OWNER |
| GET | `/share-invitations` | 보낸 초대 목록 | OWNER |
| POST | `/share-invitations` | 초대 생성 | OWNER |
| GET | `/share-invitations/received` | 받은 초대 목록 | 인증 |
| DELETE | `/share-invitations/{invitationId}` | 초대 철회 | OWNER |
| POST | `/share-invitations/{token}/accept` | 초대 수락 | 인증 |
| POST | `/share-invitations/{token}/decline` | 초대 거절 | 인증 |

- 직접 공유 `POST /shares`는 1차에서 제공하지 않습니다.
- 공유는 초대 수락 후 Grant가 생성됩니다.

### 5.9 목록 필터 기본값

- `status`를 생략하면 `IN_USE`, `STORED`를 적용하고 `DISPOSED`는 명시할 때만 포함합니다.
- `roomId`는 목록 API에서 직접 위치한 Inventory만 대상으로 합니다. 검색 API의 `roomId`는 하위 Shelf와 Box를 포함합니다.
- `shelfId`는 목록 API에서 직접 위치한 Inventory만 대상으로 합니다. 검색 API의 `shelfId`는 하위 Box를 포함합니다.
- `boxId`는 해당 Box만 대상으로 합니다.
- `categoryId`는 effective category를 기준으로 합니다.
- `scope`는 `ALL`, `OWNED`, `SHARED`이며 기본값은 `ALL`입니다.
- `hasImage`는 대표 이미지 존재 여부를 의미합니다.

## 6. 이벤트

비동기 이벤트의 단일 원본은 [이벤트 계약](generated/events.yaml)입니다.

- exchange는 `agolock.events` topic exchange를 사용합니다.
- routing key는 이벤트 타입과 동일합니다.
- 큐는 이벤트 타입 패턴을 바인딩합니다.
- 각 큐는 `agolock.dlx`를 통해 DLQ로 보냅니다.

Outbox 기준 주요 이벤트 타입:

- `inventory.created`
- `inventory.updated`
- `inventory.moved`
- `inventory.deleted`
- `image.uploaded`
- `ai.recognition.requested`
- `ai.recognition.completed`
- `ai.embedding.requested`
- `ai.embedding.completed`
- `share.invitation.created`
- `share.invitation.accepted`
- `share.invitation.declined`
- `share.created`
- `share.revoked`

`image.processed`와 `image.deleted`는 현재 소비자·바인딩이 없어 발행하지 않습니다. 소비자를 도입할 때 큐/바인딩/재처리 정책과 함께 추가합니다.

이벤트 공통 필드:

```json
{
  "eventId": "018f...",
  "eventType": "inventory.moved",
  "spaceId": "018f...",
  "occurredAt": "2026-10-19T12:00:00Z",
  "schemaVersion": 1,
  "payload": {}
}
```

- `spaceId`는 도메인 이벤트에서 필수입니다.
- `jobId`는 job 관련 이벤트에만 포함합니다.
- 소비자는 `eventId`로 멱등성을 보장합니다.
- payload에는 최소 식별자만 담고 민감 정보는 포함하지 않습니다.
- 이벤트 스키마 변경은 하위 호환성을 유지하고 `schemaVersion`을 올립니다.
- 큐 이름과 routing key는 이벤트 타입과 일치시킵니다.

## 7. 계약 문서

- HTTP API: [openapi.yaml](generated/openapi.yaml)
- 비동기 이벤트: [events.yaml](generated/events.yaml)
- OpenAPI와 이벤트 계약은 구현 후 생성물로 전환됩니다.
- 설계 단계에서는 [ADR-014](decisions/ADR-014-generated-docs-draft.md)에 따라 초안을 수동 갱신합니다.
- M0에서는 [ADR-015](decisions/ADR-015-contract-preview-mode.md)에 따라 생성 결과를 `build/contracts/`에만 기록하고, `docs/generated/` 상세 초안을 보존합니다.
- M0의 미리보기 파일은 `build/contracts/openapi.yaml`, `events.yaml`, `data-dictionary.md`이며 커밋하지 않습니다.
- M1·M2에서도 `build/contracts/` 미리보기 모드를 유지합니다. `docs/generated/` 기록 모드 전환은 [ADR-015](decisions/ADR-015-contract-preview-mode.md)에 따라 M4 또는 M8에서 판단합니다.
- 계약 변경 시 코드와 생성 스크립트를 함께 갱신합니다.

## 9. 계약 커버리지 검사

- `python3 scripts/openapi_coverage.py`는 `build/contracts/openapi.yaml`의 구현 path/method가 이 문서에 있는지 검사합니다.
- `/info`, `/share-invitations/{invitationId}/resend`, `/shared/*` 5개는 아직 계약 문서에 옮기지 않아 allowlist에 있습니다. 계약을 추가하면 allowlist에서 제거합니다.

## 10. Schema snapshot 검사

- `python3 scripts/openapi_snapshot.py`는 `build/contracts/openapi.json`과 `contracts/openapi.snapshot.json`의 request/response schema, required, enum을 비교합니다.
- 의도한 계약 변경이면 `python3 scripts/openapi_snapshot.py --update`로 snapshot을 갱신하고 review에 포함합니다.
- `docs_check.py`는 preview와 snapshot이 모두 있을 때 이 검사를 실행합니다.
