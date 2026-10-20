# 개인 물품 관리 앱 제품 사양

> 상태: 확정
> 역할: 제품 요구사항과 범위의 단일 원본
> 관련 문서: [아키텍처](architecture.md), [도메인](domain.md), [공유](sharing.md), [검색](search.md), [미디어·AI](media-ai.md), [운영](operations.md)

## 1. 개요

계층 구조 기반의 개인 물품 관리 앱입니다.

- 물건(Item 마스터)과 재고(Inventory 개별 인스턴스)를 분리합니다.
- 재고별로 상태, 위치, 사진, 설명, 가격을 독립적으로 관리합니다.
- Room, Shelf, Box 계층과 공유 권한을 지원합니다.
- 이미지 유사도 검색과 AI 인식을 제공합니다.

## 2. 목표와 비목표

### 목표

- 물건이 많아도 원하는 재고를 빠르게 찾을 수 있습니다.
- 같은 마스터라도 개별 재고의 차이를 잃지 않습니다.
- 가족·친구와 방 또는 선반 단위로 안전하게 공유합니다.
- 사진 기반 검색과 AI 입력 보조로 입력 비용을 줄입니다.

### 비목표

- 수량 기반 재고 관리
- 대여·반납·보증 만료 관리
- 그룹·조직 단위 공유
- 링크 공유, 공개 공유
- 오프라인 편집과 실시간 동기화
- 다중 통화와 회계 기능

## 3. 용어

| 용어 | 의미 |
|---|---|
| Space | 테넌트 경계이자 사용자 개인 공간 |
| Room | 방. Space에 속하는 최상위 위치 |
| Shelf | 선반. Room에 속함 |
| Box | 박스. Shelf에 속함 |
| Item | 물건 마스터. 공통 정보 |
| Inventory | 개별 재고 인스턴스. Item을 참조 |
| Effective field | 재고 값이 있으면 재고 값, 없으면 마스터 값 |
| Share | Room 또는 Shelf에 부여하는 접근 권한 |
| Grant | 특정 사용자에게 부여된 Share |
| Invitation | 수락 후 Grant가 되는 공유 초대 |
| Owner | Space 소유자 |
| VIEW | 조회 권한 |
| EDIT | 재고 오버라이드, 사진, 상태 수정 권한 |
| MANAGE | EDIT + 재고 추가·삭제·이동 권한 |

## 4. 사용자와 소유권

- 사용자는 이메일과 비밀번호로 가입합니다.
- 스테이징 환경은 초대코드로 가입을 제한합니다.
- 가입 시 사용자마다 하나의 Space가 생성됩니다.
- Room, Shelf, Box, Item, Inventory, Category, Image는 Space에 귀속됩니다.
- 공유는 Space 소유권을 넘기지 않고 접근 권한만 부여합니다.
- 1차에서 새 Item 마스터 생성은 Space 소유자만 수행합니다.
- 공유 사용자의 `MANAGE`는 접근 가능한 기존 Item에 Inventory를 추가하는 것까지 허용합니다.
- 공유 사용자는 새 Item이나 새 Category를 만들 수 없고 기존 항목만 선택합니다.
- 공유는 초대를 수락한 뒤 효력이 발생하며, 직접 공유는 1차에서 지원하지 않습니다.

## 5. 계층과 위치

- 계층은 Room, Shelf, Box의 3단계로 고정합니다.
- Inventory는 Room, Shelf, Box 중 어디에든 존재할 수 있습니다.
- Inventory는 `room_id`, `shelf_id`, `box_id` 중 정확히 하나를 가집니다.
- 하위 항목이나 재고가 있는 위치는 삭제할 수 없으며, 이동 후 삭제해야 합니다.
- 선반과 박스의 이동은 소유자만 수행할 수 있습니다.
- 재고 이동은 같은 Space 안에서만 허용합니다.
- 위치 삭제 차단은 삭제되지 않은 하위 리소스와 Inventory를 기준으로 합니다.
- 같은 위치로의 이동은 no-op으로 처리하고 이동 이력을 만들지 않습니다.

상세 규칙은 [도메인](domain.md)을 따릅니다.

## 6. 마스터와 재고

- Item은 이름, 설명, 카테고리, 가격, 사진의 기본값을 가집니다.
- Inventory는 이름, 설명, 카테고리, 가격, 사진을 오버라이드할 수 있습니다.
- 오버라이드 값이 `NULL`이면 마스터 값을 사용합니다.
- 재고 값이 있으면 재고 값을 사용하고, 없으면 마스터 값을 사용합니다.
- 마스터 수정은 오버라이드가 없는 재고에 즉시 전파됩니다.
- 마스터 공통 필드와 Item 대표 사진 수정은 소유자만 가능합니다.
- 마스터 수정 전에 영향을 받는 재고 수를 사용자에게 보여줍니다.
- 가격은 KRW 정수와 통화 코드로 저장합니다.
- MVP는 KRW 단일 통화만 허용하며 Inventory 통화 오버라이드는 지원하지 않습니다.
- 재고는 수량이 아니라 개별 물건 단위입니다.

상세 모델과 effective field 규칙은 [도메인](domain.md)과 [데이터 사전](generated/data-dictionary.md)을 따릅니다.

## 7. 상태와 생명주기

- 상태는 Inventory에만 존재합니다.
- 상태값은 `IN_USE`, `STORED`, `DISPOSED`입니다.
- 기본값은 `STORED`입니다.
- 상태 전이는 자유롭게 허용하되, `DISPOSED` 전환은 확인을 받습니다.
- `DISPOSED` 전환 요청은 서버 확인 플래그를 함께 보내야 합니다.
- `DISPOSED`는 복구할 수 있습니다.
- `DISPOSED` 재고는 기본 검색과 기본 목록에서 제외합니다.
- 폐기는 삭제가 아니며, 삭제는 별도의 soft delete로 처리합니다.

## 8. 검색과 추천

- 검색 필드는 이름, 설명, 위치, 상태입니다.
- 검색 결과는 Inventory를 기준으로 하되 마스터 단위로 그룹핑합니다.
- 검색 범위는 내 Space와 공유받은 Room/Shelf입니다.
- 공유 결과에는 출처를 표시합니다.
- `DISPOSED`는 기본 검색에서 제외합니다.
- 최근 검색어와 자주 본 물건을 토큰으로 제공합니다.
- 최근 검색어와 자주 본 물건은 서버에 사용자별로 저장합니다.
- 위치 검색은 하위 위치를 포함합니다.
- 검색 결과는 Item 그룹 단위 커서로 페이지네이션하고, 그룹당 Inventory를 제한해 반환합니다.
- 이미지 검색은 동기 임베딩과 5초 타임아웃으로 처리하며 검색 이미지 원본을 저장하지 않습니다.

상세 규칙은 [검색](search.md)을 따릅니다.

## 9. 카테고리

- 카테고리는 사용자가 자유롭게 생성합니다.
- 카테고리는 Space 소유이며 평면 구조입니다.
- 기본 카테고리는 Item에 설정합니다.
- Inventory는 카테고리를 오버라이드할 수 있습니다.
- Group By와 필터는 effective category를 기준으로 합니다.
- 카테고리 생성·수정·삭제·병합은 소유자만 가능합니다.
- 공유 사용자는 접근 가능한 항목이 참조하는 기존 카테고리만 조회하고 선택할 수 있습니다.
- 정규화된 이름은 같은 Space 안에서 유일합니다.
- 사용 중인 카테고리는 삭제하지 않고 병합을 권장합니다.
- AI는 카테고리를 제안할 수 있지만 자동 생성하지 않습니다.

## 10. 공유와 권한

- 공유 단위는 Room과 Shelf입니다.
- Box 공유, 재공유, 링크 공유는 지원하지 않습니다.
- 공유는 초대를 수락한 뒤 효력이 발생합니다.
- 직접 공유는 1차에서 지원하지 않고 3차에서 재검토합니다.
- 권한은 `VIEW < EDIT < MANAGE` 누적 구조입니다.
- `EDIT`는 재고 오버라이드, Inventory 사진, 상태 수정을 포함합니다.
- `MANAGE`는 `EDIT`를 포함하고 기존 Item에 대한 재고 추가·삭제·이동을 허용합니다.
- 신규 Item 생성, Item 대표 사진, 마스터 공통 필드 수정은 소유자만 가능합니다.
- 계층 구조, 카테고리, 공유 관리는 소유자만 가능합니다.
- Room 공유는 하위 Shelf와 Box, Inventory에 적용됩니다.
- 이미 공유된 Shelf가 있어도 Room 공유를 추가할 수 있습니다.
- 여러 grant가 있으면 가장 높은 권한을 적용합니다.
- grant를 철회해도 다른 grant는 유지됩니다.
- 초대는 이메일로 발송하고 수락 후 효력이 발생합니다.
- 초대받은 사용자는 초대를 거절할 수 있습니다.
- 방 공유 시 선반별 비공개 예외는 MVP에서 지원하지 않습니다.

상세 권한 매트릭스와 상속 알고리즘은 [공유](sharing.md)를 따릅니다.

## 11. 이미지와 AI

- 클라이언트는 이미지를 WebP로 가공해 업로드합니다.
- 서버는 presign 시 `UPLOADING` 이미지 행을 만들고 업로드 완료 후 검증합니다.
- AI 입력처럼 대상이 아직 없는 이미지는 `DRAFT` 상태로 두고 저장 시 연결합니다.
- 서버는 업로드 이미지를 재인코딩하고 EXIF를 제거합니다.
- 이미지 원본은 비공개 버킷에 저장합니다.
- 이미지 접근은 권한 확인 후 짧은 TTL의 presigned URL로 허용합니다.
- 목록 응답은 이미지 ID를 반환하고, 클라이언트는 배치 URL API로 presigned URL을 받습니다.
- 대상별 대표 이미지는 최대 1장이며, 첫 `READY` 이미지가 자동으로 대표가 됩니다.
- AI 인식 결과는 폼 자동 채움 제안이며, 사용자 확인 없이 저장하지 않습니다.
- AI 인식과 임베딩은 비동기 job으로 처리합니다.
- 이미지 검색은 예외적으로 동기 임베딩과 5초 타임아웃을 사용합니다.
- 이미지 임베딩은 Gemini 멀티모달 임베딩을 사용합니다.
- 재고 대표 사진이 있으면 재고 벡터를, 없으면 마스터 벡터를 사용합니다.
- 임베딩 모델과 차원, 버전을 저장하고 변경 시 재임베딩합니다.
- 1차 임베딩은 1408차원과 단일 모델로 고정합니다.
- 임베딩 provider 장애 시 다른 모델로 fallback하지 않습니다.
- AI provider는 설정 기반으로 선택하고 인식 장애 시 fallback합니다.
- OpenAI와 Gemini의 비용·품질은 샘플 벤치마크 후 확정합니다.

상세 파이프라인은 [미디어·AI](media-ai.md)를 따릅니다.

## 12. 기술 제약

| 영역 | 결정 |
|---|---|
| Backend | Kotlin, Spring Boot, Java 21, Gradle Kotlin DSL |
| 데이터 접근 | MyBatis, Flyway |
| Database | PostgreSQL 16 이상, pgvector |
| Frontend | React, TypeScript, Vite, TanStack Query, Tailwind, shadcn/ui |
| 인증 | 이메일+비밀번호, 스테이징 초대코드, Spring Session JDBC |
| 비동기 | RabbitMQ, Transactional Outbox |
| 이미지 | S3 호환 스토리지, 개발 환경 MinIO |
| 배포 | Docker Compose 기반 모놀리식, GitHub Actions |
| 저장소 | monorepo |

## 13. MVP 단계

### 1차

- 인증, Space, 초대코드, 초대 전용 공유
- Room, Shelf, Box 계층과 Shelf/Box 이동
- Item, Inventory, 오버라이드, 상태, 카테고리
- 소유자만 신규 Item 생성, 공유 MANAGE는 기존 Item에 Inventory 추가
- 키워드 검색, 최근 검색, 자주 본 물건, 검색 제안
- 공유 초대, 수락, 거절, 권한
- 이미지 업로드와 수동 입력
- 삭제, 이동, 이동 이력
- 최소 감사 기록

### 2차

- AI 인식
- 이미지 임베딩과 유사도 검색
- AI job 상태 UI와 재처리
- 동기 이미지 검색 타임아웃과 오류 처리
- 비용·품질 벤치마크

### 3차

- 선반별 비공개 예외
- 그룹·링크 공유
- 직접 공유 재검토
- 인앱 알림과 알림 센터
- 사진별 임베딩
- Space 간 복사
- 감사 로그 UI와 데이터 내보내기

## 14. 제외 범위

- 수량 관리
- 대여·반납 관리
- 보증 만료 알림
- 다중 통화
- 실시간 협업 편집
- 오프라인 편집
- 공개 링크 공유
- Box 단위 공유
- 재공유
- 다중 Space 소유

## 15. 요구사항 ID

| ID | 요구사항 | 설계 문서 |
|---|---|---|
| REQ-TEN-001 | 모든 테넌트 데이터는 Space에 귀속된다 | [아키텍처](architecture.md) |
| REQ-TEN-002 | 다른 Space의 리소스를 참조할 수 없다 | [데이터 사전](generated/data-dictionary.md) |
| REQ-LOC-001 | Inventory는 Room, Shelf, Box 중 정확히 하나에 위치한다 | [도메인](domain.md) |
| REQ-LOC-002 | 하위 항목이나 재고가 있는 위치는 삭제할 수 없다 | [도메인](domain.md) |
| REQ-LOC-003 | 재고 이동은 같은 Space 안에서만 허용한다 | [도메인](domain.md) |
| REQ-LOC-004 | Shelf와 Box 이동은 소유자만 수행한다 | [도메인](domain.md) |
| REQ-ITEM-001 | 재고 오버라이드가 있으면 재고 값을 사용한다 | [도메인](domain.md) |
| REQ-ITEM-002 | 마스터 수정은 오버라이드가 없는 재고에 전파된다 | [도메인](domain.md) |
| REQ-ITEM-003 | 마스터 공통 필드는 소유자만 수정한다 | [공유](sharing.md) |
| REQ-ITEM-004 | 신규 Item 생성은 소유자만 수행한다 | [공유](sharing.md) |
| REQ-STAT-001 | 상태는 Inventory에만 존재한다 | [도메인](domain.md) |
| REQ-STAT-002 | 기본 상태는 STORED이다 | [도메인](domain.md) |
| REQ-STAT-003 | DISPOSED는 기본 검색에서 제외한다 | [검색](search.md) |
| REQ-STAT-004 | DISPOSED 전환은 서버 확인 플래그를 요구한다 | [도메인](domain.md) |
| REQ-CAT-001 | 카테고리는 Space 소유이며 평면 구조이다 | [도메인](domain.md) |
| REQ-CAT-002 | Group By는 effective category를 사용한다 | [검색](search.md) |
| REQ-CAT-003 | 카테고리 이름은 정규화 후 유일하며 병합 시 참조를 갱신한다 | [도메인](domain.md) |
| REQ-SHR-001 | 공유 권한은 VIEW, EDIT, MANAGE 누적이다 | [공유](sharing.md) |
| REQ-SHR-002 | 여러 grant는 최대 권한을 적용한다 | [공유](sharing.md) |
| REQ-SHR-003 | 계층, 마스터, 카테고리, 공유 관리는 소유자만 가능하다 | [공유](sharing.md) |
| REQ-SHR-004 | 공유는 초대 수락 후 효력이 발생한다 | [공유](sharing.md) |
| REQ-SHR-005 | Item 이미지는 OWNER, Inventory 이미지는 EDIT 이상이 수정한다 | [공유](sharing.md) |
| REQ-SRH-001 | 검색은 내 Space와 공유받은 Room/Shelf를 포함한다 | [검색](search.md) |
| REQ-SRH-002 | 검색 결과는 Inventory 기준으로 마스터 그룹핑한다 | [검색](search.md) |
| REQ-SRH-003 | 최근 검색과 자주 본 물건을 제공한다 | [검색](search.md) |
| REQ-SRH-004 | 검색 결과는 Item 그룹 단위 커서로 페이지네이션한다 | [검색](search.md) |
| REQ-SRH-005 | 이미지 검색은 동기 임베딩과 5초 타임아웃을 사용한다 | [검색](search.md) |
| REQ-IMG-001 | 이미지는 비공개 저장소와 presigned URL을 사용한다 | [미디어·AI](media-ai.md) |
| REQ-IMG-002 | 서버는 이미지를 재인코딩하고 EXIF를 제거한다 | [미디어·AI](media-ai.md) |
| REQ-IMG-003 | presign 시 UPLOADING 이미지 행을 생성한다 | [미디어·AI](media-ai.md) |
| REQ-IMG-004 | 대표 이미지 승격과 보관 정책을 적용한다 | [미디어·AI](media-ai.md) |
| REQ-AI-001 | AI 결과는 사용자 확인 후 저장한다 | [미디어·AI](media-ai.md) |
| REQ-AI-002 | AI job과 이벤트 소비자는 멱등하다 | [미디어·AI](media-ai.md) |
| REQ-AI-003 | 임베딩 모델과 버전을 저장한다 | [미디어·AI](media-ai.md) |
| REQ-AI-004 | 임베딩 모델 간 fallback을 금지한다 | [미디어·AI](media-ai.md) |
| REQ-OPS-001 | 코드 변경 후 영향 문서를 갱신한다 | [AGENTS.md](../AGENTS.md) |

## 16. 확정된 설계 결정

| 결정 | ADR |
|---|---|
| Space 테넌트 경계와 공유 스키마 | [ADR-001](decisions/ADR-001-space-tenant.md) |
| Inventory 위치 모델 | [ADR-002](decisions/ADR-002-inventory-location.md) |
| 권한 모델 | [ADR-003](decisions/ADR-003-permission-model.md) |
| MyBatis 선택 | [ADR-004](decisions/ADR-004-mybatis.md) |
| RabbitMQ와 Outbox | [ADR-005](decisions/ADR-005-rabbitmq-outbox.md) |
| 세션 인증 | [ADR-006](decisions/ADR-006-session-auth.md) |
| 임베딩 모델 | [ADR-007](decisions/ADR-007-embedding-model.md) |
| Presigned 이미지 | [ADR-008](decisions/ADR-008-presigned-images.md) |
| AI 확인 저장 | [ADR-009](decisions/ADR-009-ai-confirmation.md) |
| Item 생성과 마스터 권한 | [ADR-010](decisions/ADR-010-item-creation-permissions.md) |
| 이미지 업로드 수명주기 | [ADR-011](decisions/ADR-011-image-upload-lifecycle.md) |
| 임베딩 모델·차원 버전 관리 | [ADR-012](decisions/ADR-012-embedding-versioning.md) |
| 초대 전용 공유 | [ADR-013](decisions/ADR-013-invitation-only-sharing.md) |
| 설계 단계 생성 문서 운영 | [ADR-014](decisions/ADR-014-generated-docs-draft.md) |
