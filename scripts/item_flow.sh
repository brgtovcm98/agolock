#!/usr/bin/env bash
# 항목 단위 작업 루프 게이트입니다. 규약과 대기열은 docs/implementation/PLAN-016-item-workflow-and-queue.md를 따릅니다.
# 사용: scripts/item_flow.sh <start|verify|status|merge> ...
set -euo pipefail

usage() {
  cat <<'USAGE'
사용법: scripts/item_flow.sh <명령> [인자]

명령:
  start <slug> [--base main]
      항목 워크트리 ../agolock-worktrees/<slug>와 item/<slug> 브랜치를 만듭니다.
      메인 체크아웃에서 실행하며, main이 clean이 아니거나 이미 있으면 아무것도 바꾸지 않습니다.
  verify [--scope docs|contract|backend|frontend|e2e|full]
      item/ 브랜치 워크트리에서 CI와 같은 명령을 실행하고 로그·HEAD SHA·결과를 기록합니다.
      기본값은 full입니다. 결과는 pass / fail / blocked(환경 실패 보류) 중 하나입니다.
  status [<slug>]
      대기열 항목의 브랜치·워크트리·최근 검증·병합 SHA를 출력합니다.
  merge [<slug>] [--keep] [--message "<text>"] [--partial "<이유>"]
      게이트 4종(워크트리 clean, 현재 HEAD의 full pass 검증, main clean, 병합할 커밋 존재)을
      통과할 때만 --no-ff로 병합하고 워크트리와 item/ 브랜치를 정리합니다. --keep은 정리를 생략합니다.
      --partial은 환경상 full 검증이 불가능할 때만 쓰는 예외이며, 이유가 필수입니다.

검증 로그와 상태 파일은 저장소 밖 ../agolock-worktrees/.state/에 기록합니다.
USAGE
}

usage_start() {
  cat <<'USAGE'
사용법: scripts/item_flow.sh start <slug> [--base main]

  <slug>            항목 브랜치 슬러그입니다(item/<slug>). 소문자·숫자·하이픈만 씁니다.
  --base <branch>   기준 브랜치입니다. 기본값은 main입니다.

워크트리 ../agolock-worktrees/<slug>, 브랜치 item/<slug>, 상태 파일 .state/<slug>.json을 만듭니다.
메인 체크아웃에서만 실행할 수 있고, 추적 파일이 clean이 아니면 exit 1로 거부합니다.
USAGE
}

usage_verify() {
  cat <<'USAGE'
사용법: scripts/item_flow.sh verify [--scope docs|contract|backend|frontend|e2e|full]

  docs       python3 scripts/docs_check.py
  backend    ./gradlew :backend:ktlintCheck :backend:detekt :backend:test
  frontend   pnpm install(필요 시) → generate:api → schema diff → lint → format:check → test → build
  contract   generateOpenApiPreview → normalize_openapi.py → export_events.py → docs_check.py
  e2e        pnpm install(필요 시) → pnpm exec playwright install chromium(필요 시) → pnpm test:e2e
  full       위 5개 전부(병합 전 필수). 기본값입니다.

item/ 브랜치 워크트리에서만 실행할 수 있습니다. 결과는 .state/<slug>.json의 verifications[]에 쌓입니다.
결과는 pass(전부 성공), fail(코드·테스트 실패), blocked(환경 실패 보류) 중 하나이며 merge 게이트는 pass만 인정합니다.
e2e 전에 127.0.0.1:5173 점유를 확인하며, 점유되어 있으면 다른 서버를 재사용하지 않고 보류로 기록합니다.
USAGE
}

usage_status() {
  cat <<'USAGE'
사용법: scripts/item_flow.sh status [<slug>]

slug를 생략하면 PLAN-016 대기열의 모든 항목과 상태를 출력합니다.
상태 파일이 없는 항목은 미시작으로 표시합니다. 병합 뒤 워크트리 정리가 실패한 항목은 정리 미완 경로를 함께 표시합니다.
USAGE
}

usage_merge() {
  cat <<'USAGE'
사용법: scripts/item_flow.sh merge [<slug>] [--keep] [--message "<text>"] [--partial "<이유>"]

  <slug>              생략하면 워크트리가 남아 있는 항목을 자동 선택합니다. 후보가 여럿이면 지정해야 합니다.
  --keep              병합 후 워크트리 제거와 브랜치 삭제를 생략합니다.
  --message "<text>"  병합 커밋 메시지를 대체합니다. 기본값은 "merge: <ID> <요약>"입니다.
  --partial "<이유>"  환경상 full 검증을 실행할 수 없을 때만 쓰는 예외입니다. 이유가 비어 있으면 거부합니다.
                      현재 HEAD의 pass 기록(scope 무관)을 요구하고, 사유를 상태 파일에 남깁니다.
                      --partial이 아닌 병합은 --keep을 쓰더라도 이전 partial 사유를 지웁니다.
                      cleanup_pending은 워크트리·브랜치 정리에 성공한 경우에만 지웁니다.

메인 체크아웃에서 실행합니다. 게이트 4종을 모두 통과해야 병합합니다(fail-closed).
  1) 항목 워크트리에 추적 파일 변경이 없음
  2) 현재 항목 HEAD에 대한 `scope full` + `result pass` 검증 기록이 상태 파일에 있음
  3) main 작업 디렉터리에 추적 파일 변경이 없음
  4) 항목 브랜치에 현재 main 이후 커밋이 있음
하나라도 어긋나면 병합하지 않고 이유를 출력합니다. `result blocked`(환경 실패 보류)는 2)를 만족하지 않습니다.
USAGE
}

die() {
  printf '오류: %s\n' "$*" >&2
  exit 1
}

# 저장소 공통 .git 디렉터리에서 메인 체크아웃 루트를 구합니다.
# 워크트리에는 .tools/.gradle-user/.python-packages/.playwright-browsers가 없으므로 메인 기준 도구 경로가 필요합니다.
if ! git_common_dir="$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"; then
  printf '오류: git 저장소 안에서 실행해야 합니다.\n' >&2
  exit 1
fi
MAIN_ROOT="$(cd "$(dirname "$git_common_dir")" && pwd -P)"

if [ ! -f "$MAIN_ROOT/scripts/env.sh" ]; then
  printf '오류: %s/scripts/env.sh를 찾을 수 없습니다.\n' "$MAIN_ROOT" >&2
  exit 1
fi
# shellcheck source=scripts/env.sh
source "$MAIN_ROOT/scripts/env.sh"

WORKTREES_ROOT="$(cd "$MAIN_ROOT/.." && pwd -P)/agolock-worktrees"
STATE_DIR="$WORKTREES_ROOT/.state"
LOG_DIR="$STATE_DIR/logs"
QUEUE_DOC="$MAIN_ROOT/docs/implementation/PLAN-016-item-workflow-and-queue.md"

meta_file() { printf '%s/%s.meta\n' "$STATE_DIR" "$1"; }
record_file() { printf '%s/%s.verify.jsonl\n' "$STATE_DIR" "$1"; }
state_file() { printf '%s/%s.json\n' "$STATE_DIR" "$1"; }

iso_now() { date +%Y-%m-%dT%H:%M:%S%:z; }

# 경과 시간을 밀리초로 재기 위한 현재 시각입니다. bash 5의 EPOCHREALTIME을 우선 쓰고, 없으면 초 단위로 떨어집니다.
now_ms() {
  if [ -n "${EPOCHREALTIME:-}" ]; then
    local t="${EPOCHREALTIME//[.,]/}"
    printf '%s' "$(( t / 1000 ))"
  else
    printf '%s' "$(( $(date +%s) * 1000 ))"
  fi
}

# 여러 줄 문자열을 한 줄로 눌러 meta(한 줄 단위)와 JSON에서 값을 잃지 않게 합니다.
# JSON 문자열에 넣을 수 없는 제어 문자는 공백으로 바꿉니다.
flatten_text() {
  printf '%s' "$1" | tr '\r\n\t' '   ' | tr -d '\000-\010\013\014\016-\037'
}

# JSON 문자열에 넣을 수 없는 문자를 이스케이프합니다.
json_escape() {
  flatten_text "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

meta_get() {
  local file
  file="$(meta_file "$1")"
  [ -f "$file" ] || return 0
  sed -n "s/^$2=//p" "$file" | tail -1
}

meta_set() {
  local file tmp
  file="$(meta_file "$1")"
  mkdir -p "$STATE_DIR"
  tmp="$file.tmp"
  if [ -f "$file" ]; then
    { grep -v "^$2=" "$file" || true; } > "$tmp"
  else
    : > "$tmp"
  fi
  printf '%s=%s\n' "$2" "$3" >> "$tmp"
  mv "$tmp" "$file"
}

# 메타데이터 값이 있으면 문자열, 없으면 null로 상태 파일 필드를 출력합니다.
print_state_field() {
  local slug="$1" key="$2" sep="$3" value
  value="$(meta_get "$slug" "$key")"
  if [ -n "$value" ]; then
    printf '  "%s": "%s"%s\n' "$key" "$(json_escape "$value")" "$sep"
  else
    printf '  "%s": null%s\n' "$key" "$sep"
  fi
}

# 항목 메타데이터(.meta)와 검증 기록(.verify.jsonl)에서 사람이 읽는 상태 파일(.json)을 다시 만듭니다.
# 검증 기록을 덧붙이기만 하면 되도록 분리했고, JSON 조립은 printf만 사용합니다.
write_state() {
  local slug="$1" file tmp record line first=1
  file="$(state_file "$slug")"
  tmp="$file.tmp"
  record="$(record_file "$slug")"
  mkdir -p "$STATE_DIR"
  {
    printf '{\n'
    printf '  "item": "%s",\n' "$(json_escape "$(meta_get "$slug" item)")"
    printf '  "branch": "%s",\n' "$(json_escape "$(meta_get "$slug" branch)")"
    printf '  "worktree": "%s",\n' "$(json_escape "$(meta_get "$slug" worktree)")"
    printf '  "base": "%s",\n' "$(json_escape "$(meta_get "$slug" base)")"
    printf '  "base_sha": "%s",\n' "$(json_escape "$(meta_get "$slug" base_sha)")"
    printf '  "verifications": ['
    if [ -s "$record" ]; then
      while IFS= read -r line; do
        [ -n "$line" ] || continue
        if [ "$first" -eq 1 ]; then
          first=0
          printf '\n    %s' "$line"
        else
          printf ',\n    %s' "$line"
        fi
      done < "$record"
      printf '\n  '
    fi
    printf '],\n'
    print_state_field "$slug" merged_sha ","
    print_state_field "$slug" partial_reason ","
    print_state_field "$slug" partial_head ","
    print_state_field "$slug" cleanup_pending ""
    printf '}\n'
  } > "$tmp"
  mv "$tmp" "$file"
}

record_verification() {
  local slug="$1" scope="$2" head="$3" result="$4" log="$5" reason="${6:-}"
  mkdir -p "$STATE_DIR"
  if [ -n "$reason" ]; then
    printf '{"scope": "%s", "head": "%s", "result": "%s", "log": "%s", "finished_at": "%s", "reason": "%s"}\n' \
      "$(json_escape "$scope")" "$head" "$result" "$(json_escape "$log")" "$(iso_now)" "$(json_escape "$reason")" \
      >> "$(record_file "$slug")"
  else
    printf '{"scope": "%s", "head": "%s", "result": "%s", "log": "%s", "finished_at": "%s"}\n' \
      "$(json_escape "$scope")" "$head" "$result" "$(json_escape "$log")" "$(iso_now)" \
      >> "$(record_file "$slug")"
  fi
  write_state "$slug"
}

require_main_checkout() {
  local top
  if ! top="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    die "git 작업 디렉터리 안에서 실행해야 합니다."
  fi
  if [ "$(cd "$top" && pwd -P)" != "$MAIN_ROOT" ]; then
    die "메인 체크아웃($MAIN_ROOT)에서 실행해야 합니다. 현재 작업 디렉터리: $top"
  fi
}

# clean 판정은 추적 파일 기준입니다. 항목 워크트리에서 만든 산출물처럼 커밋하지 않는 untracked 파일은 경고만 합니다.
require_clean_tracked() {
  local dir="$1" label="$2" dirty untracked
  dirty="$(git -C "$dir" status --porcelain --untracked-files=no)"
  if [ -n "$dirty" ]; then
    printf '거부: %s에 커밋되지 않은 변경이 있습니다.\n%s\n' "$label" "$dirty" >&2
    return 1
  fi
  untracked="$(git -C "$dir" status --porcelain --untracked-files=normal | sed -n 's/^?? //p')"
  if [ -n "$untracked" ]; then
    printf '경고: %s에 추적되지 않은 파일이 있습니다(진행은 계속).\n%s\n' "$label" "$untracked" >&2
  fi
  return 0
}

validate_slug() {
  case "$1" in
    ''|*[!a-z0-9-]*) die "slug는 소문자·숫자·하이픈만 쓸 수 있습니다: $1" ;;
    -*|*-) die "slug는 하이픈으로 시작하거나 끝날 수 없습니다: $1" ;;
  esac
}

require_command() {
  command -v "$1" >/dev/null 2>&1
}

# 포트 점유를 최대 2초 안에 확인합니다. 닫힌 포트에 /dev/tcp로 연결하면 tcp_syn_retries 설정에 따라
# 수십 초 걸릴 수 있으므로 ss를 우선 쓰고, 없으면 timeout으로 연결 시도 시간을 제한합니다.
port_busy() {
  local port="$1" busy=1
  if require_command ss; then
    if ss -ltn 2>/dev/null | grep -qE "[:.]${port}([^0-9]|$)"; then
      busy=0
    fi
    return "$busy"
  fi
  if timeout 2 bash -c "exec 3<>/dev/tcp/127.0.0.1/${port}" >/dev/null 2>&1; then
    busy=0
  fi
  return "$busy"
}

docker_ready() {
  require_command docker || return 1
  docker info >/dev/null 2>&1
}

playwright_ready() {
  local dir="${PLAYWRIGHT_BROWSERS_PATH:-}"
  if [ -z "$dir" ] || [ ! -d "$dir" ]; then
    return 1
  fi
  compgen -G "$dir/chromium-*" >/dev/null
}

# 명령을 터미널과 로그에 동시에 남기고 종료 코드를 반환합니다. 실패해도 스크립트를 즉시 끝내지 않습니다.
run_step() {
  local log="$1"
  shift
  printf '\n$ %s\n' "$*" | tee -a "$log"
  local status=0
  { "$@" 2>&1 | tee -a "$log"; } || status=$?
  printf '종료 코드: %s\n' "$status" >> "$log"
  return "$status"
}

# 도구 부재·서버 점유처럼 코드가 원인이 아닌 사전 조건 실패를 환경 실패(보류)로 표시합니다.
# 사유는 BLOCK_REASON으로 넘겨 verify가 result blocked 기록에 함께 남깁니다.
BLOCK_REASON=""
block_env() {
  local log="$1" reason="$2"
  BLOCK_REASON="$reason"
  printf '\n환경 실패(보류): %s — 코드 실패가 아닙니다.\n' "$reason" | tee -a "$log" >&2
}

block_early() {
  local slug="$1" scope="$2" head="$3" log="$4" reason="$5"
  printf '\n환경 실패(보류): %s — 코드 실패가 아닙니다.\n' "$reason" | tee -a "$log" >&2
  record_verification "$slug" "$scope" "$head" "blocked" "$log" "$reason"
  printf '검증 보류: %s\n로그: %s\n' "$reason" "$log" >&2
  exit 1
}

run_scope() {
  local scope="$1" log="$2"
  case "$scope" in
    docs)
      if ! require_command python3; then
        block_env "$log" "python3를 찾을 수 없습니다"
        return 2
      fi
      run_step "$log" python3 scripts/docs_check.py || return 1
      ;;
    backend)
      if ! require_command java; then
        block_env "$log" "java를 찾을 수 없습니다"
        return 2
      fi
      if [ ! -x ./gradlew ]; then
        block_env "$log" "./gradlew를 실행할 수 없습니다"
        return 2
      fi
      if ! docker_ready; then
        block_env "$log" "Docker를 사용할 수 없어 testcontainers 백엔드 테스트를 실행할 수 없습니다"
        return 2
      fi
      run_step "$log" ./gradlew :backend:ktlintCheck :backend:detekt :backend:test || return 1
      ;;
    frontend)
      if ! require_command pnpm; then
        block_env "$log" "pnpm을 찾을 수 없습니다(corepack 활성화 또는 scripts/env.sh의 .tools 확인)"
        return 2
      fi
      if [ ! -d frontend/node_modules ]; then
        if ! run_step "$log" pnpm install --frozen-lockfile; then
          block_env "$log" "pnpm install --frozen-lockfile을 실행할 수 없습니다(의존성 설치 실패)"
          return 2
        fi
      fi
      run_step "$log" pnpm generate:api || return 1
      run_step "$log" git diff --exit-code frontend/src/shared/api/schema.d.ts || return 1
      run_step "$log" pnpm lint || return 1
      run_step "$log" pnpm format:check || return 1
      run_step "$log" pnpm test || return 1
      run_step "$log" pnpm build || return 1
      ;;
    contract)
      if ! require_command python3; then
        block_env "$log" "python3를 찾을 수 없습니다"
        return 2
      fi
      if ! require_command java; then
        block_env "$log" "java를 찾을 수 없습니다"
        return 2
      fi
      if [ ! -x ./gradlew ]; then
        block_env "$log" "./gradlew를 실행할 수 없습니다"
        return 2
      fi
      run_step "$log" ./gradlew :backend:generateOpenApiPreview || return 1
      run_step "$log" python3 scripts/normalize_openapi.py || return 1
      run_step "$log" python3 scripts/export_events.py || return 1
      run_step "$log" python3 scripts/docs_check.py || return 1
      ;;
    e2e)
      local probe_start probe_elapsed probe_state
      probe_start="$(now_ms)"
      if port_busy 5173; then
        probe_state="사용 중"
      else
        probe_state="비어 있음"
      fi
      probe_elapsed=$(( $(now_ms) - probe_start ))
      printf '5173 포트 확인: %s (%d.%03ds)\n' \
        "$probe_state" $((probe_elapsed / 1000)) $((probe_elapsed % 1000)) | tee -a "$log"
      if [ "$probe_state" = "사용 중" ]; then
        block_env "$log" "127.0.0.1:5173이 이미 사용 중입니다. 다른 서버를 재사용하지 않습니다"
        return 2
      fi
      if ! require_command pnpm; then
        block_env "$log" "pnpm을 찾을 수 없습니다(corepack 활성화 또는 scripts/env.sh의 .tools 확인)"
        return 2
      fi
      if [ ! -d frontend/node_modules ]; then
        if ! run_step "$log" pnpm install --frozen-lockfile; then
          block_env "$log" "pnpm install --frozen-lockfile을 실행할 수 없습니다(의존성 설치 실패)"
          return 2
        fi
      fi
      if ! playwright_ready; then
        if ! run_step "$log" pnpm exec playwright install chromium; then
          block_env "$log" "Playwright chromium 브라우저를 설치하지 못했습니다"
          return 2
        fi
      fi
      run_step "$log" pnpm test:e2e || return 1
      ;;
    *)
      die "알 수 없는 scope: $scope"
      ;;
  esac
}

queue_slugs() {
  [ -f "$QUEUE_DOC" ] || return 0
  awk -F'|' '$2 ~ /(INV|REL)-[0-9]/ { gsub(/[ `]/, "", $3); print $3 }' "$QUEUE_DOC"
}

cmd_start() {
  local slug="" base="main"
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --base)
        [ "$#" -ge 2 ] || die "--base 값이 필요합니다."
        base="$2"
        shift 2
        ;;
      --help|-h)
        usage_start
        return 0
        ;;
      -*)
        die "알 수 없는 옵션: $1"
        ;;
      *)
        [ -z "$slug" ] || die "slug는 하나만 지정합니다."
        slug="$1"
        shift
        ;;
    esac
  done
  [ -n "$slug" ] || { usage_start >&2; exit 1; }
  validate_slug "$slug"
  require_main_checkout
  require_clean_tracked "$MAIN_ROOT" "main 작업 디렉터리" || exit 1

  local worktree_path="$WORKTREES_ROOT/$slug"
  local branch="item/$slug"
  if [ -e "$worktree_path" ]; then
    die "워크트리가 이미 있습니다: $worktree_path"
  fi
  if git -C "$MAIN_ROOT" show-ref --verify --quiet "refs/heads/$branch"; then
    die "브랜치가 이미 있습니다: $branch"
  fi
  if ! git -C "$MAIN_ROOT" rev-parse --verify --quiet "refs/heads/$base" >/dev/null; then
    die "기준 브랜치가 없습니다: $base"
  fi

  mkdir -p "$STATE_DIR" "$LOG_DIR"
  git -C "$MAIN_ROOT" worktree add -b "$branch" "$worktree_path" "$base"

  local base_sha
  base_sha="$(git -C "$MAIN_ROOT" rev-parse "$base")"
  : > "$(record_file "$slug")"
  meta_set "$slug" item "$slug"
  meta_set "$slug" branch "$branch"
  meta_set "$slug" worktree "$worktree_path"
  meta_set "$slug" base "$base"
  meta_set "$slug" base_sha "$base_sha"
  meta_set "$slug" merged_sha ""
  meta_set "$slug" partial_reason ""
  meta_set "$slug" partial_head ""
  meta_set "$slug" cleanup_pending ""
  write_state "$slug"

  printf '항목 시작: %s\n' "$slug"
  printf '  워크트리: %s\n' "$worktree_path"
  printf '  브랜치:   %s (기준 %s %s)\n' "$branch" "$base" "$base_sha"
  printf '  상태:     %s\n' "$(state_file "$slug")"
  printf '다음: %s에서 수정한 뒤 verify --scope full을 실행하세요. 병합은 메인 체크아웃에서 실행합니다.\n' "$worktree_path"
}

cmd_verify() {
  local scope="full"
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --scope)
        [ "$#" -ge 2 ] || die "--scope 값이 필요합니다."
        scope="$2"
        shift 2
        ;;
      --help|-h)
        usage_verify
        return 0
        ;;
      -*)
        die "알 수 없는 옵션: $1"
        ;;
      *)
        die "알 수 없는 인자: $1"
        ;;
    esac
  done
  case "$scope" in
    docs|backend|frontend|contract|e2e|full) ;;
    *) die "알 수 없는 scope: $scope (docs|contract|backend|frontend|e2e|full)" ;;
  esac

  local top branch slug
  if ! top="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    die "git 작업 디렉터리 안에서 실행해야 합니다."
  fi
  top="$(cd "$top" && pwd -P)"
  branch="$(git -C "$top" rev-parse --abbrev-ref HEAD)"
  case "$branch" in
    item/*) slug="${branch#item/}" ;;
    *) die "item/ 브랜치 워크트리에서만 실행할 수 있습니다. 현재 브랜치: $branch" ;;
  esac
  if [ ! -f "$(state_file "$slug")" ]; then
    die "상태 파일이 없습니다: $(state_file "$slug"). scripts/item_flow.sh start $slug 로 항목을 먼저 시작하세요."
  fi

  cd "$top"
  mkdir -p "$LOG_DIR"
  local log head
  log="$LOG_DIR/${slug}-$(date +%Y%m%dT%H%M%S)-$$.log"
  head="$(git rev-parse HEAD)"
  {
    printf '항목: %s\n' "$slug"
    printf '브랜치: %s\n' "$branch"
    printf '워크트리: %s\n' "$top"
    printf 'scope: %s\n' "$scope"
    printf 'HEAD: %s\n' "$head"
    printf '시작: %s\n' "$(iso_now)"
    printf '메인 체크아웃: %s\n' "$MAIN_ROOT"
  } > "$log"
  printf '검증 시작: %s / %s\n  HEAD: %s\n  로그: %s\n' "$slug" "$scope" "$head" "$log"

  if ! python3 -c "import yaml" >/dev/null 2>&1; then
    block_early "$slug" "$scope" "$head" "$log" "python3에서 yaml 모듈을 불러올 수 없습니다. scripts/env.sh의 PYTHONPATH를 확인하세요."
  fi

  local -a scopes_to_run
  if [ "$scope" = "full" ]; then
    scopes_to_run=(docs backend frontend contract e2e)
  else
    scopes_to_run=("$scope")
  fi

  local item_scope status=0 reason
  BLOCK_REASON=""
  for item_scope in "${scopes_to_run[@]}"; do
    status=0
    run_scope "$item_scope" "$log" || status=$?
    [ "$status" -eq 0 ] || break
  done

  if [ "$status" -eq 0 ]; then
    record_verification "$slug" "$scope" "$head" "pass" "$log"
    printf '\n검증 통과: %s / %s (HEAD %s)\n로그: %s\n' "$slug" "$scope" "$head" "$log"
  elif [ "$status" -eq 2 ]; then
    reason="${BLOCK_REASON:-환경 사전 조건을 만족하지 못했습니다}"
    record_verification "$slug" "$scope" "$head" "blocked" "$log" "$reason"
    printf '\n검증 보류: %s / %s (HEAD %s) — 코드 실패가 아닙니다.\n사유: %s\n로그: %s\n' \
      "$slug" "$scope" "$head" "$reason" "$log" >&2
    exit 1
  else
    record_verification "$slug" "$scope" "$head" "fail" "$log"
    printf '\n검증 실패: %s / %s (HEAD %s)\n로그: %s\n' "$slug" "$scope" "$head" "$log" >&2
    exit 1
  fi
}

latest_record_field() {
  local slug="$1" field="$2" record
  record="$(record_file "$slug")"
  [ -s "$record" ] || return 0
  tail -1 "$record" | sed -n "s/.*\"$field\": \"\([^\"]*\)\".*/\1/p"
}

print_status() {
  local slug="$1" merged scope result head log_path line pending partial
  if [ ! -f "$(state_file "$slug")" ]; then
    printf '%-36s %s\n' "$slug" "미시작"
    return 0
  fi
  merged="$(meta_get "$slug" merged_sha)"
  if [ -n "$merged" ]; then
    pending="$(meta_get "$slug" cleanup_pending)"
    partial="$(meta_get "$slug" partial_reason)"
    if [ -n "$pending" ]; then
      printf '%-36s 병합 %s (워크트리 정리 미완: %s)\n' "$slug" "$merged" "$pending"
    elif [ -n "$partial" ]; then
      printf '%-36s 병합 %s (부분 검증: %s)\n' "$slug" "$merged" "$partial"
    else
      printf '%-36s 병합 %s\n' "$slug" "$merged"
    fi
    return 0
  fi
  scope="$(latest_record_field "$slug" scope)"
  result="$(latest_record_field "$slug" result)"
  head="$(latest_record_field "$slug" head)"
  log_path="$(latest_record_field "$slug" log)"
  line="진행"
  if [ -n "$scope" ]; then
    line="최근 검증 ${scope} ${result} ${head:0:12}"
  else
    line="검증 기록 없음"
  fi
  if [ ! -d "$WORKTREES_ROOT/$slug" ]; then
    line="$line (워크트리 없음)"
  fi
  printf '%-36s %s\n' "$slug" "$line"
  if [ -n "$log_path" ]; then
    printf '%-36s 로그 %s\n' "" "$log_path"
  fi
}

cmd_status() {
  local slug=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --help|-h)
        usage_status
        return 0
        ;;
      -*)
        die "알 수 없는 옵션: $1"
        ;;
      *)
        [ -z "$slug" ] || die "slug는 하나만 지정합니다."
        slug="$1"
        shift
        ;;
    esac
  done

  if [ -n "$slug" ]; then
    validate_slug "$slug"
    print_status "$slug"
    return 0
  fi

  printf '항목 상태 (기준: %s)\n\n' "$WORKTREES_ROOT"
  local queue_slug seen=""
  while IFS= read -r queue_slug; do
    [ -n "$queue_slug" ] || continue
    seen="$seen $queue_slug"
    print_status "$queue_slug"
  done <<< "$(queue_slugs)"

  if [ -d "$STATE_DIR" ]; then
    local file extra
    for file in "$STATE_DIR"/*.meta; do
      [ -e "$file" ] || continue
      extra="$(basename "$file" .meta)"
      case " $seen " in
        *" $extra "*) continue ;;
      esac
      print_status "$extra"
    done
  fi
}

default_merge_message() {
  local slug="$1" first rest num id summary
  first="${slug%%-*}"
  if [ "$first" = "$slug" ]; then
    printf 'merge: %s\n' "$(printf '%s' "$slug" | tr '[:lower:]' '[:upper:]')"
    return 0
  fi
  rest="${slug#*-}"
  num="${rest%%-*}"
  id="$(printf '%s-%s' "$first" "$num" | tr '[:lower:]' '[:upper:]')"
  if [ "$rest" = "$num" ]; then
    summary=""
  else
    summary="${rest#*-}"
    summary="${summary//-/ }"
  fi
  if [ -n "$summary" ]; then
    printf 'merge: %s %s\n' "$id" "$summary"
  else
    printf 'merge: %s\n' "$id"
  fi
}

resolve_slug() {
  local requested="$1" candidates=() file slug
  if [ -n "$requested" ]; then
    validate_slug "$requested"
    if [ ! -f "$(state_file "$requested")" ]; then
      die "상태 파일이 없습니다: $(state_file "$requested")"
    fi
    printf '%s\n' "$requested"
    return 0
  fi
  if [ -d "$STATE_DIR" ]; then
    for file in "$STATE_DIR"/*.meta; do
      [ -e "$file" ] || continue
      slug="$(basename "$file" .meta)"
      if [ -d "$WORKTREES_ROOT/$slug" ]; then
        candidates+=("$slug")
      fi
    done
  fi
  case "${#candidates[@]}" in
    0) die "워크트리가 남아 있는 항목이 없습니다. scripts/item_flow.sh start <slug>로 항목을 시작하세요." ;;
    1) printf '%s\n' "${candidates[0]}" ;;
    *) die "항목이 여러 개입니다. slug를 지정하세요: ${candidates[*]}" ;;
  esac
}

cmd_merge() {
  local keep=0 message="" requested="" partial=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --keep)
        keep=1
        shift
        ;;
      --message)
        [ "$#" -ge 2 ] || die "--message 값이 필요합니다."
        message="$2"
        shift 2
        ;;
      --partial)
        [ "$#" -ge 2 ] || die "--partial 값에 병합 사유가 필요합니다."
        [ -n "${2//[[:space:]]/}" ] || die "--partial 사유가 비어 있습니다. 환경상 full 검증이 불가능한 이유를 적어야 합니다."
        partial="$(flatten_text "$2")"
        shift 2
        ;;
      --help|-h)
        usage_merge
        return 0
        ;;
      -*)
        die "알 수 없는 옵션: $1"
        ;;
      *)
        [ -z "$requested" ] || die "slug는 하나만 지정합니다."
        requested="$1"
        shift
        ;;
    esac
  done

  require_main_checkout
  local slug
  slug="$(resolve_slug "$requested")"
  local branch="item/$slug"
  local worktree_path="$WORKTREES_ROOT/$slug"

  if [ ! -d "$worktree_path" ]; then
    die "항목 워크트리가 없습니다: $worktree_path"
  fi
  require_clean_tracked "$worktree_path" "항목 워크트리" || exit 1

  local item_head branch_head
  item_head="$(git -C "$worktree_path" rev-parse HEAD)"
  branch_head="$(git -C "$MAIN_ROOT" rev-parse "refs/heads/$branch")"
  if [ "$item_head" != "$branch_head" ]; then
    die "워크트리 HEAD($item_head)와 브랜치 $branch($branch_head)가 다릅니다. 워크트리 상태를 확인하세요."
  fi

  local record
  record="$(record_file "$slug")"
  if [ ! -s "$record" ]; then
    die "검증 기록이 없습니다: $record. verify를 먼저 실행하세요."
  fi

  # 게이트 2: 기본 요구는 현재 HEAD의 full pass입니다. --partial은 환경상 full이 불가능할 때만 쓰는 예외입니다.
  local last_summary full_pass=0 any_pass=0
  last_summary="scope=$(latest_record_field "$slug" scope) result=$(latest_record_field "$slug" result) head=$(latest_record_field "$slug" head)"
  if grep -F "\"head\": \"$item_head\"" "$record" | grep -F '"result": "pass"' >/dev/null; then
    any_pass=1
  fi
  if grep -F "\"head\": \"$item_head\"" "$record" | grep -F '"result": "pass"' | grep -F '"scope": "full"' >/dev/null; then
    full_pass=1
  fi
  if [ "$full_pass" -eq 0 ]; then
    if [ -z "$partial" ]; then
      die "현재 HEAD($item_head)에 대한 full 검증 pass 기록이 없습니다. 마지막 기록: $last_summary. verify --scope full을 실행하거나, 환경상 불가능하면 merge --partial \"<이유>\"를 쓰세요."
    fi
    if [ "$any_pass" -eq 0 ]; then
      die "현재 HEAD($item_head)에 대한 pass 검증 기록이 없습니다. 마지막 기록: $last_summary. --partial도 pass 기록은 필요합니다."
    fi
    printf '경고: --partial 병합입니다. full 검증 없이 병합합니다.\n  사유: %s\n  사유를 PLAN-016 항목 기록에 반드시 남기고, 사유가 해소되면 full 검증을 다시 실행하세요.\n' \
      "$partial" >&2
  elif [ -n "$partial" ]; then
    printf '참고: 현재 HEAD에 full 검증 pass 기록이 있으므로 --partial 사유를 기록하지 않습니다.\n' >&2
    partial=""
  fi

  require_clean_tracked "$MAIN_ROOT" "main 작업 디렉터리" || exit 1

  # 앞선 커밋이 없으면 --no-ff 병합 커밋이 만들어지지 않으므로 병합으로 보지 않습니다.
  local ahead
  ahead="$(git -C "$MAIN_ROOT" rev-list --count "HEAD..$branch")"
  if [ "$ahead" -eq 0 ]; then
    die "병합할 커밋이 없습니다: $branch에 현재 HEAD 이후 커밋이 없습니다. 항목 커밋을 먼저 확인하세요."
  fi

  if [ -z "$message" ]; then
    message="$(default_merge_message "$slug")"
  fi

  if ! git -C "$MAIN_ROOT" merge --no-ff -m "$message" "$branch"; then
    printf '거부: 병합이 실패했습니다. 충돌이면 git merge --abort로 중단하고 항목 워크트리에서 main을 반영한 뒤 재검증하세요(PLAN-016 7.2).\n' >&2
    exit 1
  fi

  local merged_sha
  merged_sha="$(git -C "$MAIN_ROOT" rev-parse HEAD)"
  meta_set "$slug" merged_sha "$merged_sha"
  if [ -n "$partial" ]; then
    meta_set "$slug" partial_reason "$partial"
    meta_set "$slug" partial_head "$item_head"
  else
    # 앞선 --partial 병합의 흔적은 최신 full 검증 병합과 어긋나므로 병합 직후 지웁니다. --keep은 정리에만 영향을 줍니다.
    meta_set "$slug" partial_reason ""
    meta_set "$slug" partial_head ""
  fi
  write_state "$slug"
  printf '병합 완료: %s\n  병합 커밋: %s\n  메시지: %s\n' "$slug" "$merged_sha" "$message"
  if [ -n "$partial" ]; then
    printf '  부분 검증: %s (상태 파일: %s)\n' "$partial" "$(state_file "$slug")"
  fi

  if [ "$keep" -eq 1 ]; then
    printf '  --keep: 워크트리와 브랜치를 남깁니다.\n    워크트리 %s\n    브랜치 %s\n' "$worktree_path" "$branch"
    return 0
  fi

  local cleanup_failed=""
  if git -C "$MAIN_ROOT" worktree remove "$worktree_path"; then
    printf '  워크트리 제거: %s\n' "$worktree_path"
  else
    cleanup_failed="$worktree_path"
    printf '경고: 워크트리를 제거하지 못했습니다(추적되지 않은 파일이 있을 수 있음): %s\n' "$worktree_path" >&2
    printf '      내용을 확인한 뒤 다음을 실행하세요:\n' >&2
    printf '        git worktree remove --force %s\n' "$worktree_path" >&2
    printf '        git branch -d %s\n' "$branch" >&2
  fi
  if git -C "$MAIN_ROOT" branch -d "$branch"; then
    printf '  브랜치 삭제: %s\n' "$branch"
  else
    printf '경고: 브랜치를 삭제하지 못했습니다(워크트리가 남아 있으면 워크트리 제거 뒤 다시 실행): %s\n' "$branch" >&2
    [ -n "$cleanup_failed" ] || cleanup_failed="$worktree_path"
  fi
  if [ -n "$cleanup_failed" ]; then
    meta_set "$slug" cleanup_pending "$cleanup_failed"
    write_state "$slug"
    printf '      정리 전 상태: scripts/item_flow.sh status %s 에 정리 미완으로 표시됩니다.\n' "$slug" >&2
  else
    # 정리에 성공한 경우에만 이전 정리 실패 흔적을 지웁니다. partial 값은 병합 직후 이미 정리했습니다.
    meta_set "$slug" cleanup_pending ""
    write_state "$slug"
  fi
}

case "${1:-}" in
  start)
    shift
    cmd_start "$@"
    ;;
  verify)
    shift
    cmd_verify "$@"
    ;;
  status)
    shift
    cmd_status "$@"
    ;;
  merge)
    shift
    cmd_merge "$@"
    ;;
  help|-h|--help)
    usage
    ;;
  '')
    usage >&2
    exit 2
    ;;
  *)
    printf '오류: 알 수 없는 명령: %s\n\n' "$1" >&2
    usage >&2
    exit 2
    ;;
esac
