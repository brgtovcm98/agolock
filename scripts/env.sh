#!/usr/bin/env bash
# 저장소에 고정된 로컬 도구를 현재 셸에 적용합니다.
# 사용: source scripts/env.sh
# 도구가 없으면 bash scripts/install-tools.sh를 먼저 실행합니다.
AGOLOCK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export AGOLOCK_ROOT

if [ -x "$AGOLOCK_ROOT/.tools/jdk-21/bin/java" ]; then
  export JAVA_HOME="$AGOLOCK_ROOT/.tools/jdk-21"
fi

if [ -d "$AGOLOCK_ROOT/.tools/node-22" ]; then
  export PATH="$AGOLOCK_ROOT/.tools/node-22/bin:$PATH"
fi

if [ -d "$AGOLOCK_ROOT/.tools/pnpm" ]; then
  export PATH="$AGOLOCK_ROOT/.tools/pnpm/bin:$PATH"
fi

if [ -d "$AGOLOCK_ROOT/.tools/bin" ]; then
  export PATH="$AGOLOCK_ROOT/.tools/bin:$PATH"
fi

if [ -d "$AGOLOCK_ROOT/.tools/jdk-21/bin" ]; then
  export PATH="$AGOLOCK_ROOT/.tools/jdk-21/bin:$PATH"
fi

export GRADLE_USER_HOME="$AGOLOCK_ROOT/.gradle-user"
export NPM_CONFIG_CACHE="$AGOLOCK_ROOT/.npm-cache"
export PYTHONPATH="$AGOLOCK_ROOT/.python-packages${PYTHONPATH:+:$PYTHONPATH}"
export PLAYWRIGHT_BROWSERS_PATH="$AGOLOCK_ROOT/.playwright-browsers"
