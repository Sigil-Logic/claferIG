# Alloy 6.2.0 vs. Alloy 4.2 Behavioral Baseline Comparison

**Status**: Frozen snapshot
**Version**: 1.0.0
**Date**: 2026-09-12
**Project**: HOARDE (Sigil-Logic Clafer fork, epic [HOARDE#608](https://github.com/Sigil-Logic/HOARDE/issues/608))
**Issue**: [Sigil-Logic/clafer#5](https://github.com/Sigil-Logic/clafer/issues/5)

---

## Purpose

The documented re-baselining evidence (SL-DOM-P04) required by [clafer#5](https://github.com/Sigil-Logic/clafer/issues/5)'s fourth acceptance criterion: compare claferIG's instance-generation behavior under the ported Alloy 6.2.0 toolchain against the frozen Alloy 4.2 reference baseline ([`.evidence/alloy42-baseline/`](../alloy42-baseline), 16 models, 1801 instances, captured under [clafer#7](https://github.com/Sigil-Logic/clafer/issues/7)).  Captures use the same recipe (`claferIG --all=2 --json` over `test/positive/*.cfr`, 120 s/model); comparisons were produced by [`scripts/compare-alloy-baselines.py`](../../scripts/compare-alloy-baselines.py), which checks, per model: exit-code parity, instance-count parity, instance-set equality modulo enumeration order (canonicalized JSON multisets), and enumeration-order drift.

## Verdict

**No re-baselining is required.**  Against the frozen Alloy 4.2 reference, the Alloy 6.2.0 port is, on all 16 corpus models:

- **exit-code identical** (including the three pre-existing nonzero exits: `i220`, `i243`, `waitingLine` — the JSONGenerator StringValue limitation, unchanged),
- **instance-count identical** (1801 instances in total), and
- **instance-set identical** modulo enumeration order.

Only two models enumerate in a different order (`TeamLeaderMemberContractor`, 1778 instances; `i83_individual-scope-…`, 6 instances); their instance *sets* are equal.  Enumeration order was never a documented claferIG contract, and no test encodes it, so the frozen 4.2 baseline remains the valid instance-set reference and no expected-output test changed.

Additionally, the Alloy 6.2.0 capture is **deterministic across architectures**: the x86_64 Linux CI capture and a local aarch64 macOS capture are instance-set identical on all 16 models — the first claferIG runtime evidence ever produced on Apple Silicon (see [`../alloy62-arm64-probe/`](../alloy62-arm64-probe) for the solver-level probe).

## Contents

| File | Comparison |
|---|---|
| `compare-42-vs-62-x86_64.txt` | Frozen 4.2 reference vs. 6.2 capture on x86_64 Linux CI ([run 34713586523](https://github.com/Sigil-Logic/claferIG/actions/runs/34713586523), artifact `alloy-baseline`) — **the canonical comparison** (same environment class as the frozen reference) |
| `compare-42-vs-62-arm64-local.txt` | Frozen 4.2 reference vs. 6.2 capture on local aarch64 macOS |
| `compare-62-x86_64-vs-arm64.txt` | 6.2 x86_64 CI capture vs. 6.2 local aarch64 capture (cross-architecture determinism) |
| `environments.txt` | Environment and provenance records of all three captures (SL-DOM-P05) |

## Reproduction

```bash
# frozen 4.2 reference
mkdir baseline-42 && tar xzf .evidence/alloy42-baseline/baseline.tar.gz -C baseline-42
# fresh 6.2 capture (after make build on the ported tree)
bash scripts/capture-alloy-baseline.sh 2 baseline-62
python3 scripts/compare-alloy-baselines.py baseline-42 baseline-62
```

---

*HOARDE Claude (working with Frank Zeyda)*

<!--
Local Variables:
auto-fill-mode: nil
End:
-->
