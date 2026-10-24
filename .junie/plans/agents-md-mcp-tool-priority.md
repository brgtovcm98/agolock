---
sessionId: session-261024-180945-yp49
---

# Requirements

### Overview & Goals

`AGENTS.md`에 JetBrains MCP 도구(jbcontext, IntelliJ IDEA MCP)의 우선 사용 규칙을 **8번 항목**으로 추가합니다. 목적은 AI 에이전트가 "코드 위치 탐색 → 타입·참조·문제 확인 → 편집" 흐름에서 어떤 도구를 먼저 쓸지 저장소 차원에서 일관되게 정하는 것입니다.

현재 저장소에는 `AGENTS.md`(7개 항목)만 있고 `CLAUDE.md`는 존재하지 않습니다. 사용자 요청에 따라 `CLAUDE.md` 작성은 이번 범위에서 제외하며, 나중에 만들 때 8번 항목을 그대로 재사용할 수 있도록 **자기완결적인 독립 절**로 작성합니다.

### Scope

**In Scope**

- `AGENTS.md`에 8번 항목(`JetBrains MCP 도구 우선 사용`) 신설
- 8번 항목 안에 도구별 사용 규칙(jbcontext / IntelliJ IDEA MCP)과 사용 순서 3단계 기술
- 기존 1~7번 항목의 번호·내용은 변경하지 않음

**Out of Scope**

- `CLAUDE.md` 생성 및 수정 (사용자가 추후 직접 작성)
- `scripts/docs_check.py`의 `markdown_files()`, `.github/workflows/docs.yml` paths, `README.md` 문서 표에 `CLAUDE.md` 등록
- `.codex/config.toml` 등 MCP 서버 연결 설정 변경
- `docs/index.yaml` 등록 및 `last_verified_commit` 갱신 (AGENTS.md는 인덱스 대상 문서가 아님)

### Functional Requirements

| ID | 요구사항 | 완료 조건 |
|---|---|---|
| FR-1 | `AGENTS.md`에 8번 항목이 추가된다 | `## 8.` 제목이 파일 끝에 있고, 1~7번 항목 텍스트가 그대로 유지된다 |
| FR-2 | MCP 연결 여부 게이트를 명시한다 | "연결되어 있으면 우선 사용, 연결되어 있지 않으면 기본 도구로 진행"이 절 첫 문단에 있다 |
| FR-3 | jbcontext 규칙을 기술한다 | ① 위치를 모르면 `code_search` 우선 ② 파일명·심볼명을 알면 Grep·Read, 의미 검색 금지 ③ 쿼리는 자연어로 구체적으로 |
| FR-4 | IntelliJ IDEA MCP 규칙을 기술한다 | ① 타입 정보·심볼 정의/참조·에러/경고·열린 파일/선택 영역 파악에 사용 ② 읽기·쓰기는 기본 도구, IDE 도구는 분석 전용 |
| FR-5 | 사용 순서를 3단계로 기술한다 | 위치 탐색(jbcontext) → 타입·참조·문제 확인(IntelliJ IDEA MCP) → 편집(기본 편집 도구) |

### Non-Functional Requirements

- **참조 무결성**: `docs/implementation/PLAN-013-ui-review-followups.md`, `docs/implementation/PLAN-016-item-workflow-and-queue.md`가 `AGENTS.md`의 3·4·7번 항목을 번호로 참조하므로, 기존 번호를 밀거나 재배치하지 않습니다.
- **문서 규칙 준수**: 한국어 본문, 코드 식별자·도구명은 영어, 단원 기호(`§`) 미사용, 표와 짧은 불릿 우선(`AGENTS.md` 7번 항목).
- **검사 통과**: `python3 scripts/docs_check.py`가 통과해야 합니다(`AGENTS.md`는 이미 링크·단원 기호 검사 대상).

# Technical Design

### Current Implementation

- `AGENTS.md`(루트, 133줄)는 1번 먼저 읽을 것 ~ 7번 문서 작성 규칙의 7개 절로 구성되며, 각 절은 `## N. 제목` 형식이고 본문은 `- ~합니다.` 불릿과 표를 씁니다.
- `scripts/docs_check.py`의 `markdown_files()`는 `README.md`, `AGENTS.md`, `docs/**/*.md`만 검사합니다. 즉 루트에 새 파일을 만들면 검사 대상이 아니지만, `AGENTS.md` 수정은 링크·단원 기호 검사에 그대로 포함됩니다.
- `.github/workflows/docs.yml`은 `docs/**`, `design/**`, `README.md`, `AGENTS.md`, `scripts/**` 변경 시 `docs_check.py`를 실행합니다. 이번 변경은 이 트리거에 이미 포함됩니다.
- `docs/index.yaml`의 `code_paths`에 `AGENTS.md`를 가진 문서는 없습니다 → `docs_impact.py`는 "영향받는 문서가 없습니다"를 출력하며, `last_verified_commit` 갱신 대상도 아닙니다.
- 저장소 어디에도 MCP·jbcontext 서술은 없습니다(`.codex/config.toml`에 `[mcp_servers.idea]` URL 한 줄만 존재). 따라서 8번 항목이 MCP 도구 규칙의 최초 기록입니다.

### Key Decisions

1. **8번 항목으로 끝에 추가** — 기존 3·4·7번 항목을 번호로 참조하는 문서가 있어 재배치·재번호를 피합니다. 7번(문서 작성 규칙) 뒤에 붙어 읽기 흐름도 자연스럽습니다.
2. **연결 게이트를 fail-open으로 명시** — "연결되어 있지 않으면 실패로 처리하지 않고 기본 도구로 진행"을 절 첫 문단에 둡니다. MCP가 없는 환경(CI, 다른 에이전트)에서 규칙이 작업을 막지 않게 하기 위함입니다.
3. **도구 이름은 태스크 문구 그대로 `code_search` 사용, CLI 형태 병기 안 함** — "연결 안 되어 있으면 넘어간다"는 원칙과 일관되게 MCP 도구 이름만 적습니다. (전역 `~/.junie/AGENTS.md`는 CLI `jbcontext search`를 쓰므로, 필요하면 나중에 한 줄 추가로 보완 가능)
4. **문체·구조는 `AGENTS.md` 기존 절을 따름** — `###` 하위 제목 + `- ~합니다.` 불릿 + 번호 목록. 명령형("써라")이 아닌 기존 문서의 서술체로 통일합니다.
5. **`CLAUDE.md` 미생성** — 사용자가 추후 작성 예정. 8번 항목을 다른 파일에서도 그대로 쓸 수 있는 독립 절로 작성해 재사용 가능하게 합니다.

### Proposed Changes

`AGENTS.md` 끝(7번 항목 마지막 줄 뒤)에 아래 절을 추가합니다.

```markdown
## 8. JetBrains MCP 도구 우선 사용

다음 MCP 서버가 연결되어 있으면 우선 사용합니다. 연결되어 있지 않으면 실패로 처리하지 않고 기본 도구로 진행합니다.

### jbcontext

- 코드 위치나 구현을 모를 때 가장 먼저 `code_search`를 사용합니다.
- 정확한 파일명이나 심볼명을 아는 경우에는 Grep·Read를 사용하고, 의미 검색은 이때 사용하지 않습니다.
- 쿼리는 자연어로 구체적으로 작성합니다.

### IntelliJ IDEA MCP

- 타입 정보, 심볼 정의·참조, 에러·경고 확인, 현재 열린 파일과 선택 영역 파악에 사용합니다.
- 파일 읽기·쓰기는 기본 도구로 하고, IDE MCP 도구는 분석과 의미 파악 용도로만 사용합니다.

### 사용 순서

1. 위치를 모르면 jbcontext `code_search`로 찾습니다.
2. 찾은 파일의 타입·참조·문제는 IntelliJ IDEA MCP로 확인합니다.
3. 편집은 기본 편집 도구로 진행합니다.
```

추가 위치는 `AGENTS.md` 133번째 줄(7번 항목의 `- 빈 placeholder 문서를 만들지 않습니다.` 다음)이며, 기존 1~7번 항목 텍스트는 한 글자도 바꾸지 않습니다.

### Risks

| 위험 | 영향 | 대응 |
|---|---|---|
| 절 삽입 위치를 잘못 잡아 기존 번호가 밀림 | PLAN-013·PLAN-016의 "AGENTS.md 3·4·7번 항목" 참조가 어긋남 | 파일 끝 append만 수행하고, 수정 후 `git diff AGENTS.md`로 기존 줄이 삭제되지 않았음을 확인 |
| `CLAUDE.md`가 없어 Claude Code 사용자는 이 규칙을 못 봄 | 규칙 적용 범위가 Junie/Codex로 한정 | 이번 범위에서 제외(사용자 결정). 8번 항목을 독립 절로 작성해 추후 `CLAUDE.md`에 그대로 복사 가능하게 유지 |
| 도구명 표기 불일치(`code_search` vs `jbcontext search`) | 에이전트가 잘못된 명령을 시도 | MCP 도구 이름 `code_search`만 기술하고, CLI 표기는 넣지 않음(연결 없으면 넘어간다는 원칙과 일관) |
| `docs_check.py`의 단원 기호·링크 검사 | 추가 텍스트에 `§` 또는 상대 링크 오류가 있으면 CI 실패 | 새 절에 링크·`§`를 넣지 않고, 커밋 전 `python3 scripts/docs_check.py` 실행 |

# Testing

### Validation Approach

문서 전용 변경이므로 코드 테스트 대신 저장소의 문서 검사 스크립트와 diff 검토로 검증합니다.

```bash
python3 scripts/docs_check.py
git diff AGENTS.md
```

### Key Scenarios

| 시나리오 | 기대 결과 |
|---|---|
| `docs_check.py` 실행 | `문서 검사 통과: N개 인덱스 문서, M개 Markdown, 계약 검사 포함` 출력, 종료 코드 0 (`M`이 기존보다 그대로 — 새 파일을 만들지 않으므로) |
| `git diff AGENTS.md` 확인 | 추가된 줄만 있고(`+` 라인만), 1~7번 항목 라인에 `-`(삭제) 변경이 없음 |
| 8번 항목 존재 확인 | `grep -n "^## 8\." AGENTS.md` 결과가 1건, 파일 마지막 절 |
| 요구사항 매핑 확인 | 8번 항목 안에 연결 게이트 문단, `code_search`, Grep·Read 예외, IntelliJ IDEA MCP 2개 불릿, 사용 순서 3단계가 모두 존재(FR-2~FR-5) |
| 외부 참조 정합성 | PLAN-013·PLAN-016의 "AGENTS.md 3·4·7번 항목" 서술이 변경 없이 유효 |

### Edge Cases

- 새 절에 `§` 문자나 존재하지 않는 상대 링크가 들어가면 `docs_check.py`가 실패합니다 → 링크 없는 텍스트로만 작성합니다.
- 파일 끝 개행이 사라지면 diff가 지저분해집니다 → 마지막 줄 뒤 개행 1개를 유지합니다.
- `docs_impact.py`는 영향 문서 없음을 출력하는 것이 정상입니다(AGENTS.md는 `code_paths` 대상 아님).

### Test Changes

테스트 코드 변경 없음. `scripts/docs_check.py`와 `docs.yml` 트리거는 이미 `AGENTS.md`를 포함하므로 추가 등록이 필요 없습니다.

# Delivery Steps

### ✓ Step 1: AGENTS.md에 MCP 도구별 사용 규칙 절 신설
`AGENTS.md` 끝에 8번 항목의 뼈대와 도구별 규칙이 추가되어, 에이전트가 MCP 연결 시 어떤 도구를 먼저 쓸지 알 수 있다.

- `AGENTS.md` 133번째 줄(7번 항목 마지막 불릿 다음)에 `## 8. JetBrains MCP 도구 우선 사용` 절을 append
- 절 첫 문단에 연결 게이트 명시: "연결되어 있으면 우선 사용, 연결되어 있지 않으면 실패로 처리하지 않고 기본 도구로 진행"
- `### jbcontext` 하위 절에 3개 불릿 추가: ① 위치·구현을 모르면 `code_search` 우선 ② 파일명·심볼명을 알면 Grep·Read 사용, 의미 검색 금지 ③ 쿼리는 자연어로 구체적으로
- `### IntelliJ IDEA MCP` 하위 절에 2개 불릿 추가: ① 타입 정보, 심볼 정의·참조, 에러·경고, 열린 파일·선택 영역 파악에 사용 ② 읽기·쓰기는 기본 도구, IDE 도구는 분석·의미 파악 전용
- 기존 1~7번 항목은 한 줄도 삭제·수정하지 않고, 문체는 기존 절과 동일한 `- ~합니다.` 서술체 유지

### ✓ Step 2: 사용 순서 절 추가 및 기존 참조 무결성 검증
8번 항목이 사용 순서 3단계까지 완성되고, 기존 번호 참조와 문서 검사가 모두 깨지지 않은 상태로 확인된다.

- 8번 항목 마지막에 `### 사용 순서` 하위 절과 번호 목록 3단계 추가: ① 위치를 모르면 jbcontext `code_search` ② 찾은 파일의 타입·참조·문제는 IntelliJ IDEA MCP ③ 편집은 기본 편집 도구
- 새 절에 단원 기호(`§`)나 상대 링크를 넣지 않았는지 확인(문서 작성 규칙 준수)
- `git diff AGENTS.md`로 추가 라인만 있고 기존 1~7번 항목 라인에 삭제가 없음을 확인
- `docs/implementation/PLAN-013-ui-review-followups.md`, `docs/implementation/PLAN-016-item-workflow-and-queue.md`가 참조하는 "AGENTS.md 3·4·7번 항목" 서술이 여전히 유효한지 대조
- `python3 scripts/docs_check.py`를 실행해 통과(종료 코드 0) 확인