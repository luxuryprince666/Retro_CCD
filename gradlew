#!/usr/bin/env sh
set -eu
VERSION="8.13"
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/retroccd"
GRADLE_HOME="$BASE/gradle-$VERSION"
ZIP="$BASE/gradle-$VERSION-bin.zip"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$BASE"
  echo "[RetroCCD] Downloading Gradle $VERSION (one-time setup)..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  else
    echo "curl or wget is required to download Gradle." >&2
    exit 1
  fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
