#!/usr/bin/env bash
# M0에서 검증한 로컬 도구를 .tools/에 설치합니다.
# CI는 GitHub Actions의 setup-java, setup-node, pnpm/action-setup을 사용합니다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/.tools"
DOWNLOADS="$TOOLS/downloads"
NODE_VERSION="22.23.3"
PNPM_VERSION="10.34.7"
GRADLE_VERSION="8.14.3"
JDK_VERSION="21.0.12.1+1"

mkdir -p "$TOOLS" "$DOWNLOADS" "$TOOLS/bin" "$TOOLS/pnpm"

download() {
  local url="$1"
  local target="$2"
  if [ -s "$target" ]; then
    echo "이미 있음: $target"
    return
  fi
  echo "다운로드: $url"
  curl -fL --retry 3 --connect-timeout 20 -o "$target" "$url"
}

download \
  "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${JDK_VERSION//+/%2B}/OpenJDK21U-jdk_x64_linux_hotspot_${JDK_VERSION//+/_}.tar.gz" \
  "$DOWNLOADS/jdk21.tar.gz"

download \
  "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
  "$DOWNLOADS/node22.tar.xz"

download \
  "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
  "$DOWNLOADS/gradle.zip"

if [ ! -d "$TOOLS/jdk-21" ]; then
  tar -xzf "$DOWNLOADS/jdk21.tar.gz" -C "$TOOLS"
  ln -sfn "$TOOLS/jdk-${JDK_VERSION}" "$TOOLS/jdk-21"
fi

if [ ! -d "$TOOLS/node-22" ]; then
  tar -xJf "$DOWNLOADS/node22.tar.xz" -C "$TOOLS"
  ln -sfn "$TOOLS/node-v${NODE_VERSION}-linux-x64" "$TOOLS/node-22"
fi

if [ ! -d "$TOOLS/gradle-${GRADLE_VERSION}" ]; then
  python3 -m zipfile -e "$DOWNLOADS/gradle.zip" "$TOOLS"
  find "$TOOLS/gradle-${GRADLE_VERSION}" -type f \( -name '*.so' -o -name '*.sh' \) -exec chmod +x {} +
  chmod +x "$TOOLS/gradle-${GRADLE_VERSION}/bin/gradle"
  ln -sfn "$TOOLS/gradle-${GRADLE_VERSION}" "$TOOLS/gradle"
fi

if [ ! -x "$TOOLS/pnpm/bin/pnpm" ]; then
  PATH="$TOOLS/node-22/bin:$PATH" \
    npm install --global --prefix "$TOOLS/pnpm" "pnpm@${PNPM_VERSION}"
fi

ln -sfn "$TOOLS/jdk-21/bin/java" "$TOOLS/bin/java"
ln -sfn "$TOOLS/node-22/bin/node" "$TOOLS/bin/node"
ln -sfn "$TOOLS/pnpm/bin/pnpm" "$TOOLS/bin/pnpm"
ln -sfn "$TOOLS/gradle/bin/gradle" "$TOOLS/bin/gradle"

if ! PYTHONPATH="$ROOT/.python-packages" python3 -c 'import yaml, psycopg' >/dev/null 2>&1; then
  download "https://bootstrap.pypa.io/get-pip.py" "$TOOLS/get-pip.py"
  python3 "$TOOLS/get-pip.py" --target "$ROOT/.python-packages" --no-warn-script-location
  PYTHONPATH="$ROOT/.python-packages" python3 -m pip install \
    --disable-pip-version-check \
    --cache-dir "$ROOT/.pip-cache" \
    --target "$ROOT/.python-packages" \
    -r "$ROOT/scripts/requirements.txt"
fi

echo
echo "로컬 도구 설치 완료"
echo "  source scripts/env.sh"
echo "  ./gradlew :backend:test"
echo "  pnpm install"
