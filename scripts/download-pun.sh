#!/usr/bin/env bash
set -euo pipefail

# Define the data path using DEVENV_ROOT
DATA_PATH="$DEVENV_ROOT/clj/resources"
BASE_URL="https://raw.githubusercontent.com/8ta4/pun-data/4b5a2c1eeb992d2c1b8faea2488768eaac6be9dc"

# Create the resources directory if it doesn't exist
mkdir -p "$DATA_PATH"

echo "Downloading and decompressing data to $DATA_PATH..."

download_file() {
  local url="$1"
  local out="$2"

  if command -v wget >/dev/null 2>&1; then
    wget -O "$out" "$url"
  elif command -v curl >/dev/null 2>&1; then
    curl -fsSL "$url" -o "$out"
  else
    echo "Error: need either wget or curl installed." >&2
    exit 1
  fi
}

download_file "$BASE_URL/normalized.edn.gz" "$DATA_PATH/normalized.edn.gz"
gzip -d -f "$DATA_PATH/normalized.edn.gz"

download_file "$BASE_URL/ipa.edn.gz" "$DATA_PATH/ipa.edn.gz"
gzip -d -f "$DATA_PATH/ipa.edn.gz"

echo "Done."
