#!/usr/bin/env bash
# Capture the behavioral baseline of claferIG instance generation against
# whatever Alloy version the tree builds with (introduced under
# Sigil-Logic/clafer#7 as capture-alloy42-baseline.sh to freeze the Alloy 4.2
# reference, .evidence/alloy42-baseline/; renamed and reused under
# Sigil-Logic/clafer#5 to capture Alloy 6.2 behavior for comparison).
#
# Usage: capture-alloy-baseline.sh [SCOPE] [OUTDIR]
#
# Runs `claferIG --all=<SCOPE> --json <model>.cfr` over test/positive/*.cfr,
# capturing per model: the produced instance files (model.cfr.<n>.data, JSON
# content), stdout, stderr, and the exit code.  Environment, provenance, and
# invocation metadata are recorded in <OUTDIR>/environment.txt for
# reproducibility (SL-DOM-P05).  Requires a prior `make build` (jars and
# MiniSat natives staged next to the claferIG binary) and GNU timeout
# (Linux/CI).
#
# Model-level nonzero exits (e.g., the pre-existing StringValue limitation
# of JSONGenerator) are evidence and are recorded, not failed on;
# harness-level failures (missing tools/binary/corpus, timeouts, invocation
# errors) fail the capture so structurally-present-but-invalid evidence is
# never published (Cycle 1 review, HOARDE Codex).
set -euo pipefail

SCOPE="${1:-2}"
OUT="${2:-baseline-out}"

# Refuse to overlay a prior capture: stale files would corrupt counts/diffs.
if [ -e "$OUT" ] && [ -n "$(ls -A "$OUT" 2>/dev/null)" ]; then
  echo "error: output directory '$OUT' exists and is not empty; refusing to overlay a prior capture" >&2
  exit 1
fi

# Preflight: required tools, staged binary, nonempty corpus.
command -v timeout > /dev/null || { echo "error: GNU timeout not found (this capture runs on Linux/CI)" >&2; exit 1; }
command -v stack > /dev/null || { echo "error: stack not found" >&2; exit 1; }
BIN="$(stack path --local-install-root)/bin"
[ -x "$BIN/claferIG" ] || { echo "error: claferIG binary not staged at $BIN (run 'make build' first)" >&2; exit 1; }
shopt -s nullglob
corpus=(test/positive/*.cfr)
[ "${#corpus[@]}" -gt 0 ] || { echo "error: empty corpus (test/positive/*.cfr)" >&2; exit 1; }

mkdir -p "$OUT"
{
  echo "date-utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "claferIG: $("$BIN/claferIG" --version 2>&1 | head -1)"
  echo "java: $(java -version 2>&1 | head -1)"
  echo "arch: $(uname -m)  os: $(uname -s)"
  echo "invocation: claferIG --all=$SCOPE --json <model>.cfr  (timeout 120s per model)"
  echo "corpus: test/positive/*.cfr (${#corpus[@]} models)"
  echo "claferIG-commit: $(git rev-parse HEAD)"
  echo "clafer-sibling-commit: $(git -C ../clafer rev-parse HEAD 2>/dev/null || echo unavailable)"
  if [ -n "${GITHUB_SHA:-}" ]; then
    echo "github-sha: $GITHUB_SHA (on pull_request events this is the synthetic merge commit)"
  fi
  if [ -n "${GITHUB_RUN_ID:-}" ]; then
    echo "github-run: $GITHUB_RUN_ID"
  fi
} > "$OUT/environment.txt"

for f in "${corpus[@]}"; do
  base="$(basename "$f" .cfr)"
  work="$OUT/$base"
  mkdir -p "$work"
  cp "$f" "$work/model.cfr"
  rc=0
  ( cd "$work" && timeout 120 "$BIN/claferIG" --all="$SCOPE" --json model.cfr > stdout.txt 2> stderr.txt ) || rc=$?
  echo "$rc" > "$work/exit.txt"
  # 124 = timeout; 125-127 = timeout-tool/invocation failures: harness-level, fail the capture.
  if [ "$rc" -ge 124 ] && [ "$rc" -le 127 ]; then
    echo "error: harness-level failure on $base (exit $rc: timeout or invocation failure)" >&2
    exit 1
  fi
done

echo "baseline captured under $OUT: $(find "$OUT" -name '*.data' | wc -l) instance files across ${#corpus[@]} models"
