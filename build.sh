#!/usr/bin/env sh
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 8.9+ is required (or open the project in Android Studio)." >&2
  exit 1
fi
exec gradle :app:assembleDebug "$@"
