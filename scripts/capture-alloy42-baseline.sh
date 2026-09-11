#!/usr/bin/env bash
# Capture the Alloy 4.2 behavioral baseline of claferIG instance generation
# (Sigil-Logic/clafer#7); feeds re-baselining for the Alloy 6.2 modernization
# (Sigil-Logic/clafer#5).
#
# Usage: capture-alloy42-baseline.sh [SCOPE] [OUTDIR]
#
# Runs `claferIG --all=<SCOPE> --json <model>.cfr` over test/positive/*.cfr,
# capturing per model: the produced instance files (model.cfr.<n>.data, JSON
# content), stdout, stderr, and the exit code.  Environment and invocation
# metadata are recorded in <OUTDIR>/environment.txt for reproducibility
# (SL-DOM-P05).  Requires a prior `make build` (jars and MiniSat natives
# staged next to the claferIG binary).
set -uo pipefail
SCOPE="${1:-2}"
OUT="${2:-baseline-out}"
BIN="$(stack path --local-install-root)/bin"
mkdir -p "$OUT"
{
  echo "date-utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "claferIG: $("$BIN/claferIG" --version 2>&1 | head -1)"
  echo "java: $(java -version 2>&1 | head -1)"
  echo "arch: $(uname -m)  os: $(uname -s)"
  echo "invocation: claferIG --all=$SCOPE --json <model>.cfr  (timeout 120s per model)"
  echo "corpus: test/positive/*.cfr"
  echo "commit: $(git rev-parse HEAD)"
} > "$OUT/environment.txt"
for f in test/positive/*.cfr; do
  base="$(basename "$f" .cfr)"
  work="$OUT/$base"
  mkdir -p "$work"
  cp "$f" "$work/model.cfr"
  ( cd "$work" && timeout 120 "$BIN/claferIG" --all="$SCOPE" --json model.cfr > stdout.txt 2> stderr.txt; echo $? > exit.txt )
done
echo "baseline captured under $OUT: $(find "$OUT" -name '*.data' | wc -l) instance files across $(ls "$OUT" | grep -vc environment.txt) models"
