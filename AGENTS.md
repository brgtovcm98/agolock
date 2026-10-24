# AGENTS.md

이 저장소에서 작업하는 AI 에이전트의 읽기 순서, 단일 원본, 문서 갱신 규칙을 정의합니다.

## 1. 먼저 읽을 것

1. `docs/index.yaml`에서 작업 범위에 해당하는 문서를 찾습니다.
2. `docs/product-spec.md`에서 요구사항과 범위를 확인합니다.
3. `docs/architecture.md`에서 시스템 경계와 불변식을 확인합니다.
4. 변경할 코드 경로에 매핑된 문서를 읽습니다.
5. 화면 작업이면 `docs/frontend.md`와 `docs/design-system.md`를 함께 읽습니다.
6. 설계 결정이 걸린 작업이면 `docs/decisions/README.md`와 관련 ADR을 읽습니다.
7. 구현 작업이면 `docs/implementation-roadmap.md`에서 현재 마일스톤과 선행 조건을 확인합니다.

문서 전체를 무조건 읽지 않습니다. `docs/index.yaml`의 `summary`, `code_paths`, `read_when`을 사용해 필요한 문서만 선택합니다.

## 2. 단일 원본

| 대상 | 단일 원본 | 다른 문서의 처리 |
|---|---|---|
| 요구사항·범위 | `docs/product-spec.md` | 링크만 사용 |
| 구조·테넌트·보안 불변식 | `docs/architecture.md` | 링크만 사용 |
| 도메인 규칙 | `docs/domain.md` | 링크만 사용 |
| 권한·공유 | `docs/sharing.md` | 링크만 사용 |
| 검색 | `docs/search.md` | 링크만 사용 |
| 이미지·임베딩·AI·MQ | `docs/media-ai.md` | 링크만 사용 |
| API 계약 | `docs/generated/openapi.yaml` | `docs/contracts.md`는 규칙과 링크만 |
| 이벤트 계약 | `docs/generated/events.yaml` | `docs/contracts.md`는 규칙과 링크만 |
| 스키마 | `docs/generated/data-dictionary.md` | 다른 문서에서 DDL을 재정의하지 않음 |
| 화면·흐름 | `docs/frontend.md` | 링크만 사용 |
| 시각 디자인·토큰 | `docs/design-system.md` | 링크만 사용 |
| 운영·테스트·제품 단계 로드맵 | `docs/operations.md` | 링크만 사용 |
| 구현 마일스톤·작업 계획 | `docs/implementation-roadmap.md` | 제품 단계 범위는 `docs/operations.md`를 링크 |
| 설계 결정 | `docs/decisions/ADR-*.md` | 설계 문서는 결정을 링크 |

`docs/generated/` 아래 파일은 생성물입니다. 직접 수정하지 않고 생성 스크립트를 실행합니다. 구현 착수 전에는 [ADR-014](docs/decisions/ADR-014-generated-docs-draft.md)에 따라 세 문서를 설계 초안으로 수동 갱신할 수 있으며, 각 파일 머리말의 설계 초안 표시를 유지합니다.

## 3. 코드 변경 후 문서 갱신 절차

1. 변경 파일 목록을 확인합니다.
   ```bash
   git diff --name-only
   ```
2. `docs/index.yaml`의 `code_paths`와 대조해 영향을 받는 문서를 찾습니다.
   ```bash
   python3 scripts/docs_impact.py
   ```
3. 해당 문서의 동작·불변식·예시를 갱신합니다.
4. 결정이 바뀌었으면 새 ADR을 추가하거나 기존 ADR을 `Superseded`로 표시합니다.
5. OpenAPI, 이벤트, 데이터 사전을 재생성합니다.
6. `docs/index.yaml`의 `last_verified_commit`을 현재 커밋 SHA로 갱신합니다.
7. 문서 검사를 실행합니다.
   ```bash
   python3 scripts/docs_check.py
   ```
8. 코드와 문서가 어긋난 부분이 남아 있으면 작업을 완료하지 않습니다.

항목 단위 작업은 [PLAN-016](docs/implementation/PLAN-016-item-workflow-and-queue.md)의 워크트리·검증·리뷰·승인·병합 루프를 따릅니다.
`main`에서 직접 고치는 것은 초기 부트스트랩과 긴급 수정뿐이며, 항목 작업은 `item/<slug>` 브랜치 워크트리에서 수행합니다.

## 4. 불변식

- 모든 테넌트 데이터는 `space_id`를 가지며, 다른 Space의 리소스를 참조할 수 없습니다.
- Inventory는 `room_id`, `shelf_id`, `box_id` 중 정확히 하나를 가집니다.
- 공유 권한은 `VIEW < EDIT < MANAGE` 누적이며, 여러 grant는 최대 권한을 적용합니다.
- 공유는 초대 수락 후 효력이 발생하며, 직접 공유는 1차에서 지원하지 않습니다.
- 신규 Item 생성, Item 마스터 공통 필드, Item 이미지, 계층, 카테고리, 공유 관리는 소유자만 변경할 수 있습니다.
- 공유 사용자의 `MANAGE`는 기존 Item에 Inventory를 추가·삭제·이동하는 것까지 허용합니다.
- `EDIT`는 재고 오버라이드, Inventory 이미지, 상태 수정을 포함합니다.
- 상태는 Inventory에만 존재하며 기본값은 `STORED`입니다.
- `DISPOSED` 전환은 서버 확인 플래그를 요구하고, `DISPOSED` 재고는 기본 검색과 기본 목록에서 제외합니다.
- 이미지 원본은 비공개 버킷에 저장하고, 권한 확인 후 짧은 TTL의 presigned URL을 발급합니다.
- presign 시 `UPLOADING` 이미지 행을 만들고, AI 입력용 이미지는 `DRAFT`로 관리합니다.
- 서버는 업로드된 이미지를 재인코딩하고 EXIF를 제거한 뒤 사용합니다.
- AI 인식 결과는 제안일 뿐이며, 사용자 확인 없이 저장하지 않습니다.
- AI job과 이벤트 소비자는 멱등하게 작성합니다.
- 임베딩 provider 장애 시 모델 간 fallback을 하지 않습니다.
- PATCH는 JSON Merge Patch를 사용하고, 버전이 있는 리소스는 `version`을 필수로 받습니다.

## 5. 명령어

구현 단계에서 아래 명령을 사용합니다. 아직 코드가 없는 단계에서는 문서 검사만 실행합니다.

```bash
# 문서
python3 scripts/docs_check.py
python3 scripts/docs_impact.py

# 백엔드 (구현 후)
./gradlew test
./gradlew check

# 프론트엔드 (구현 후)
pnpm lint
pnpm test
pnpm build

# 로컬 인프라 (구현 후)
docker compose -f infra/docker-compose.yml up -d
```

## 6. ADR 작성 기준

다음 중 하나에 해당하면 ADR을 작성합니다.

- 되돌리기 비용이 큰 구조 결정
- 여러 대안을 비교한 기술 선택
- 테넌트, 권한, 데이터 일관성, 보안에 영향을 주는 결정
- 기존 ADR을 뒤집는 결정

ADR 형식:

```text
제목
상태
날짜
맥락
결정
결과
대안
링크
```

## 7. 문서 작성 규칙

- 문서는 한국어로 작성하고, 코드 식별자와 API 필드는 영어를 사용합니다.
- 단원 기호를 사용하지 않고 "2번 항목", "3번 항목"처럼 표기합니다.
- 표와 짧은 문단을 우선 사용합니다.
- 다이어그램은 Mermaid로 작성합니다.
- 링크는 상대 경로를 사용합니다.
- 하나의 문서가 5페이지를 넘으면 분할하고, 1페이지 이하로 짧아지면 부모 문서에 병합합니다.
- 빈 placeholder 문서를 만들지 않습니다.
