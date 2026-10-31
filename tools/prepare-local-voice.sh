#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
file=app/libs/sherpa-onnx-1.12.40.aar
sha=a7699d6a2e638a38791b2459cb0099c7577e6e5fca6b8ae916742c872a618b82
mkdir -p app/libs
if ! printf '%s  %s\n' "$sha" "$file" | sha256sum -c --status 2>/dev/null; then
  trap 'rm -f "$file.part"' EXIT
  curl --fail --location --proto '=https' --proto-redir '=https' --retry 3 \
    https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.40/sherpa-onnx-1.12.40.aar -o "$file.part"
  printf '%s  %s\n' "$sha" "$file.part" | sha256sum -c -
  mv "$file.part" "$file"
fi
printf '%s  %s\n' "$sha" "$file" | sha256sum -c -
