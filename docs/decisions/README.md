# 설계 결정 기록

> 상태: 확정
> 역할: ADR 목록과 상태의 단일 원본

## ADR 목록

| ADR | 제목 | 상태 | 관련 문서 |
|---|---|---|---|
| [ADR-001](ADR-001-space-tenant.md) | Space 테넌트 경계와 공유 스키마 | 승인됨 | [아키텍처](../architecture.md) |
| [ADR-002](ADR-002-inventory-location.md) | Inventory 위치 모델 | 승인됨 | [도메인](../domain.md) |
| [ADR-003](ADR-003-permission-model.md) | 누적 권한 모델 | 승인됨 | [공유](../sharing.md) |
| [ADR-004](ADR-004-mybatis.md) | MyBatis와 Flyway 선택 | 승인됨 | [아키텍처](../architecture.md) |
| [ADR-005](ADR-005-rabbitmq-outbox.md) | RabbitMQ와 Transactional Outbox | 승인됨 | [미디어·AI](../media-ai.md) |
| [ADR-006](ADR-006-session-auth.md) | 이메일+비밀번호와 세션 쿠키 | 승인됨 | [운영](../operations.md) |
| [ADR-007](ADR-007-embedding-model.md) | Gemini 멀티모달 임베딩 | 승인됨 | [미디어·AI](../media-ai.md) |
| [ADR-008](ADR-008-presigned-images.md) | Presigned URL 기반 이미지 접근 | 승인됨 | [미디어·AI](../media-ai.md) |
| [ADR-009](ADR-009-ai-confirmation.md) | AI 결과 확인 후 저장 | 승인됨 | [미디어·AI](../media-ai.md) |
| [ADR-010](ADR-010-item-creation-permissions.md) | Item 생성과 마스터 수정 권한 | 승인됨 | [공유](../sharing.md) |
| [ADR-011](ADR-011-image-upload-lifecycle.md) | 이미지 업로드 수명주기와 임시 이미지 | 승인됨 | [미디어·AI](../media-ai.md) |
| [ADR-012](ADR-012-embedding-versioning.md) | 임베딩 모델과 차원 버전 관리 | 승인됨 | [미디어·AI](../media-ai.md) |
| [ADR-013](ADR-013-invitation-only-sharing.md) | 초대 전용 공유 | 승인됨 | [공유](../sharing.md) |
| [ADR-014](ADR-014-generated-docs-draft.md) | 설계 단계 생성 문서 운영 | 승인됨 | [AGENTS.md](../../AGENTS.md) |
| [ADR-015](ADR-015-contract-preview-mode.md) | M0 계약 생성 미리보기 모드 | 승인됨 | [계약](../contracts.md) |
| [ADR-016](ADR-016-neo-brutal-design-system.md) | E안 네오 브루탈 디자인 시스템 | 승인됨 | [디자인 시스템](../design-system.md) |
| [ADR-019](ADR-019-search-acl-pagination.md) | 검색 ACL과 Item 그룹 페이지네이션 | 승인됨 | [검색](../search.md), [PLAN-007](../implementation/PLAN-007-search.md) |
| [ADR-020](ADR-020-registration-action-ia.md) | 등록 행동 중심 정보 구조 | 승인됨 | [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md) |

ADR-017·018은 [PLAN-006](../implementation/PLAN-006-media-messaging.md)의 이미지 후처리·보관 정책용으로 예약된 번호이며 아직 작성 전입니다.

## 작성 규칙

- 결정을 뒤집으면 기존 ADR을 `Superseded`로 표시하고 새 ADR을 링크합니다.
- ADR은 결정의 배경과 대안을 남기며, 상세 구현은 설계 문서에 둡니다.
- 날짜는 `YYYY-MM-DD` 형식을 사용합니다.
- 관련 문서와 코드 경로를 링크합니다.
