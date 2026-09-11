# Alloy 4.2 Behavioral Baseline

**Status**: Frozen snapshot
**Version**: 1.0.0
**Date**: 2026-09-10
**Project**: Clafer Toolchain (Sigil Logic)
**Issue**: [clafer#7](https://github.com/Sigil-Logic/clafer/issues/7)

---

This directory freezes the **Alloy 4.2 / claferIG 0.5.1 behavioral baseline** of instance generation over the `test/positive` corpus, captured on an x86_64 Linux GitHub Actions runner (run [34554645482](https://github.com/Sigil-Logic/claferIG/actions/runs/34554645482)).  It exists to feed the **Alloy 6.2 modernization** ([clafer#5](https://github.com/Sigil-Logic/clafer/issues/5)): after the upgrade, re-run the same capture and diff against this snapshot to identify enumeration-order, instance-set, and output-format changes that need documented re-baselining.

## Contents

| File | Purpose |
|---|---|
| `environment.txt` | Capture environment and exact invocation (SL-DOM-P05 reproducibility) |
| `exit-codes.txt` | Per-model exit code and instance count |
| `manifest.sha256` | SHA-256 of every captured file (SL-DOM-P04 evidence integrity) |
| `provenance.txt` | Exact two-repository provenance (claferIG head/base and sibling clafer SHAs) — added per the Cycle 1 review; the frozen artifact itself is unchanged |
| `baseline.tar.gz` | The full capture tree: per-model `model.cfr`, `model.cfr.<n>.data` (JSON instances), `stdout.txt`, `stderr.txt`, `exit.txt` |

Tarball SHA-256: `a5a8241d6920a6d07577a53837c64c8a4341fa4600fbbf9b8fb1f88c08c18e87`
Totals: 16 models, 1801 instance files (13 models enumerate cleanly at scope 2; 3 models — `i220`, `i243`, `waitingLine` — exit nonzero, captured as-is: all three hit the pre-existing upstream `JSONGenerator` limitation "addValue … does not accept StringValues", preserved verbatim by the aeson port).

## Regeneration

```bash
make lib && make build
bash scripts/capture-alloy42-baseline.sh 2 baseline-out
```

Run on x86_64 (Linux or CI): claferIG cannot execute on Apple Silicon under Alloy 4.2 (no arm64 MiniSat prover native — see the [field note](https://github.com/Sigil-Logic/clafer/issues/5#issuecomment-5627743710)).  The CI workflow (`.github/workflows/ci.yml`) performs this capture on every run and uploads it as the `alloy42-baseline` artifact; this committed copy is the frozen reference and is not regenerated automatically.

---
HOARDE Claude (working with Frank Zeyda)

<!--
Local Variables:
auto-fill-mode: nil
End:
-->
