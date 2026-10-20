# ADR-011: 이미지 업로드 수명주기와 임시 이미지

- 상태: 승인됨
- 날짜: 2026-10-20

## 맥락

이미지는 presigned URL로 직접 업로드하고, 업로드 완료 후 서버가 객체를 검증하고 후처리합니다. 기존 데이터 사전은 `image`의 MIME, 크기, 해상도, 해시를 NOT NULL로 두어 presign 시점에 이미지 행을 만들 수 없었습니다. AI 사진 입력은 Item 생성 전에 시작되므로 대상 Item이나 Inventory도 아직 없습니다.

## 결정

- presign 시 `status=UPLOADING`인 `image` 행을 생성합니다.
- `UPLOADING` 단계에서는 실제 MIME, 크기, 해상도, 해시를 nullable로 둡니다.
- 업로드 완료 API가 객체 존재, 선언 크기, 매직 바이트를 검증하고 실제 메타데이터를 기록한 뒤 `PENDING`으로 전환합니다.
- `READY` 상태에서만 MIME, 크기, 너비, 높이, SHA-256을 필수로 요구합니다.
- AI 입력처럼 대상이 아직 없는 이미지는 `DRAFT` 상태로 두고 24시간 후 정리합니다.
- `DRAFT` 이미지는 `UPLOADING` 완료 후 대상 Item 또는 Inventory에 연결할 때 `PENDING`으로 전환합니다.
- `DRAFT`는 생성자의 개인 Space에만 만들 수 있습니다. 공유 Space의 새 Item 생성은 1차에서 소유자만 수행하므로 공유 Space draft는 지원하지 않습니다.
- 대상별 대표 이미지는 최대 1장이며, 첫 `READY` 이미지가 자동으로 대표가 됩니다.
- 대표 이미지가 삭제되거나 실패하면 가장 최근 `READY` 이미지를 자동 승격합니다.
- 대상별 이미지는 최대 10장으로 제한합니다.
- `UPLOADING`과 `DRAFT`는 24시간, `FAILED`는 7일, soft delete된 이미지는 30일 후 원본과 썸네일을 함께 물리 삭제합니다.

## 결과

- presign과 complete의 책임이 명확해집니다.
- AI 입력이 Item 생성 전에 동작할 수 있습니다.
- 미완료 업로드와 임시 이미지를 정리하는 job과 관측 지표가 필요합니다.
- 이미지 상태가 늘어나 UI와 소비자 상태 처리가 조금 복잡해집니다.

## 대안

- 업로드 완료 시점에만 이미지 행 생성: presign 응답의 `imageId`와 `complete` 경로가 성립하지 않습니다.
- 별도 `image_upload` 테이블: 책임 분리는 명확하지만 테이블과 전환 로직이 늘어납니다.
- 공개 버킷 사용: 보안 요구에 맞지 않습니다.

## 링크

- [미디어·AI](../media-ai.md)
- [데이터 사전](../generated/data-dictionary.md)
- [API 계약](../contracts.md)
