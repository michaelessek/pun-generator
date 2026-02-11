# Pun Generator

`Pun Generator` (`pun`) generates obvious puns from one or more target words.

## Attribution

This project is based on the original `pun` project by **8ta4**.

- Original repository: https://github.com/8ta4/pun
- Original author profile: https://github.com/8ta4

This repository keeps the original mechanism for pun generation and adds a simple web interface plus API ergonomics around the same core logic.

This repository now includes:
- A web UI (`GET /`) for interactive use in a browser.
- A mode switch in the UI to choose between `ours` and `ssaamm` generation styles.
- A JSON API (`POST /api/puns`) for integrations.
- The original CLI flow (`pbpaste | pun`) via the Haskell executable.

## How It Works

1. A Clojure server loads precomputed phrase recognizability scores and IPA transcriptions.
2. Input words are transliterated to IPA (Epitran).
3. Similar sounding words are found via Levenshtein distance on IPA.
4. Recognizable phrases are rewritten by substituting similar words.

The core generation logic is shared across web, API, and CLI usage.

## Quick Start (Web UI)

```bash
git clone https://github.com/michaelessek/pun-generator.git
cd pun
```

Download required data files:

```bash
DEVENV_ROOT="$(pwd)" bash scripts/download-pun.sh
```

Start the server:

```bash
cd clj
clj -M -m server
```

Open:

`http://localhost:3000`

## Generator Modes

The web UI includes two tabs:

- `Our Generator`: the original project-style generation logic (larger result sets, recognizability-filtered phrase substitutions).
- `Ssaamm Style`: an alternative ranking style inspired by `ssaamm/pun-generator` (returns top ranked substitutions, default limit `10`).

Mode behavior is also available in the API via the `mode` field.

## API Usage

Endpoint:

`POST http://localhost:3000/api/puns`

Accepted request bodies:

1. Array of words:

```json
["pun", "joke"]
```

2. Object with newline-separated input:

```json
{ "input": "pun\njoke" }
```

3. Object with targets:

```json
{ "targets": ["pun", "joke"] }
```

Optional mode selector:

```json
{ "input": "pun", "mode": "ours" }
```

```json
{ "input": "pun", "mode": "ssaamm", "limit": 10 }
```

`mode` defaults to `ours`.
`ssaamm` mode returns ranked substitutions in the style of `ssaamm/pun-generator`.
`limit` applies to `ssaamm` mode (default `10`, max `100`).

Example:

```bash
curl -sS http://localhost:3000/api/puns \
  -H "content-type: application/json" \
  -d '{"input":"pun","mode":"ssaamm","limit":10}'
```

## CLI Usage

If you use the Haskell executable script from this repo's `devenv` setup:

```bash
pbpaste | pun
```

The CLI calls the same local Clojure server under the hood.

## Environment Notes

- `clj` is required to run the server.
- `curl` or `wget` is required for data download scripts.
- `flite`/`lex_lookup` improves IPA lookup quality for Epitran.  
  If missing, you may see a warning and reduced quality.

## Repository Layout

- `clj/src/server.clj`: HTTP API + pun generation service.
- `clj/src/core.clj`: IPA transliteration helpers.
- `clj/resources/index.html`: web interface.
- `hs/app/Main.hs`: CLI client that posts to the local server.
- `clj/src/build.clj`: offline data-building pipeline.
- `scripts/download-pun.sh`: fetches required precomputed data.

## Data

Precomputed `normalized.edn` and `ipa.edn` are fetched from the pinned `pun-data` revision in `scripts/download-pun.sh`. They are generated artifacts and are not committed in this repo.
