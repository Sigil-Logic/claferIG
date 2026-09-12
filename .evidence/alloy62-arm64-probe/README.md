# Alloy 6.2.0 Solver-Stack Probe on Apple Silicon (darwin/arm64)

**Status**: Frozen snapshot
**Version**: 1.0.0
**Date**: 2026-09-11
**Project**: HOARDE (Sigil-Logic Clafer fork, epic [HOARDE#608](https://github.com/Sigil-Logic/HOARDE/issues/608))
**Issue**: [Sigil-Logic/clafer#5](https://github.com/Sigil-Logic/clafer/issues/5)

---

## Purpose

Evidence (SL-DOM-P04) that the Alloy 6.2.0 distribution from Maven Central (`org.alloytools:org.alloytools.alloy.dist:6.2.0`) runs its **native MiniSat prover** — the UNSAT-core-capable solver claferIG requires — on an aarch64 macOS host.  This is the load-bearing fact behind the [clafer#5](https://github.com/Sigil-Logic/clafer/issues/5) scope item "refresh or drop the bundled native libraries": Alloy 4.2 ships x86-only MiniSat prover natives, making claferIG completely non-functional on Apple Silicon (see the field note on the issue).  Alloy 6.2.0 bundles `native/darwin/arm64/libminisatprover.dylib` (plus `libminisat`, `libglucose`, `plingeling`, `electrod`) inside the dist jar and self-extracts them at run time, so after the Alloy 6.2 port claferIG needs **no** bundled `lib/` natives and becomes locally buildable and testable on aarch64 hosts for the first time.

## Contents

| File | What it shows |
|---|---|
| `environment.txt` | Host, JDK, jar coordinate and SHA-256, model provenance (SL-DOM-P05) |
| `solvers-darwin-arm64.txt` | `alloy solvers` on this host: platform reported as `darwin/arm64`; `minisat.prover` present as a JNI solver |
| `natives.txt` | `alloy natives`: the per-platform native inventory bundled in the 6.2.0 dist jar (`darwin/arm64` column marked as the current platform) |
| `sat-model.als` | Clafer-generated Alloy model (clafer 0.5.1 master `e430cf7`, from `test/positive/ACCDemo_attributedFeatureModels.cfr`) |
| `exec-minisatprover-sat.txt` | `alloy exec -s minisat.prover` on `sat-model.als`: solves **SAT** natively on arm64 (exit 0) |
| `unsat-model.als` | Hand-written unsatisfiable model |
| `exec-minisatprover-unsat.txt` | `alloy exec -s minisat.prover` on `unsat-model.als`: reports **UNSAT** (exit 0) — the prover path exercised end-to-end |

## Reproduction

```bash
curl -sLO https://repo1.maven.org/maven2/org/alloytools/org.alloytools.alloy.dist/6.2.0/org.alloytools.alloy.dist-6.2.0.jar
shasum -a 256 org.alloytools.alloy.dist-6.2.0.jar   # 6037cbee...
java -jar org.alloytools.alloy.dist-6.2.0.jar solvers
java -jar org.alloytools.alloy.dist-6.2.0.jar exec -f -s minisat.prover sat-model.als
java -jar org.alloytools.alloy.dist-6.2.0.jar exec -f -s minisat.prover unsat-model.als
```

This probe is a one-off frozen artifact captured on 2026-09-11 during the [clafer#5](https://github.com/Sigil-Logic/clafer/issues/5) audit; the full audit and migration plan live in the sibling repository at [`doc/alloy-6.2-migration.md`](https://github.com/Sigil-Logic/clafer/blob/feature/5-alloy-6.2/doc/alloy-6.2-migration.md).

---

*HOARDE Claude (working with Frank Zeyda)*

<!--
Local Variables:
auto-fill-mode: nil
End:
-->
