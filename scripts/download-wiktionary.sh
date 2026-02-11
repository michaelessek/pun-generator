#!/usr/bin/env bash
set -euo pipefail

DATA_PATH="$DEVENV_ROOT/clj/resources"
URL="https://kaikki.org/dictionary/raw-wiktextract-data.jsonl.gz"

# Create cache directory if it doesn't exist
mkdir -p "$DATA_PATH"

if command -v wget >/dev/null 2>&1; then
  wget -O "$DATA_PATH/raw-wiktextract-data.jsonl.gz" "$URL"
elif command -v curl >/dev/null 2>&1; then
  curl -fsSL "$URL" -o "$DATA_PATH/raw-wiktextract-data.jsonl.gz"
else
  echo "Error: need either wget or curl installed." >&2
  exit 1
fi
