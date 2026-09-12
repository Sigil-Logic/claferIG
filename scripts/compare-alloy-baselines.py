#!/usr/bin/env python3
# Compare two claferIG behavioral-baseline captures (Sigil-Logic/clafer#5).
#
# Usage: compare-alloy-baselines.py <old-capture-dir> <new-capture-dir>
#
# Each capture directory holds one subdirectory per corpus model, as produced
# by scripts/capture-alloy-baseline.sh: model.cfr, stdout.txt, stderr.txt,
# exit.txt, and enumerated instance files model.cfr.<n>.data (JSON when the
# capture ran with --json).  For the frozen Alloy 4.2 reference, extract
# .evidence/alloy42-baseline/baseline.tar.gz first.
#
# Comparison dimensions, per model (the clafer#5 re-baselining criteria):
#   - exit code parity
#   - instance count parity
#   - instance-set equality modulo enumeration order (canonicalized JSON
#     multiset; falls back to exact text when a .data file is not JSON)
#   - enumeration-order drift (same set, different sequence)
# Exit status: 0 when every model is set-equal (order drift allowed and
# reported), 1 otherwise.  The output table is the input for the documented
# re-baselining rationale; it makes no green/red judgement beyond set parity.

import json
import sys
from pathlib import Path


def canonical(path: Path) -> str:
    text = path.read_text()
    try:
        return json.dumps(json.loads(text), sort_keys=True, separators=(",", ":"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return text


def instances(model_dir: Path) -> list[str]:
    def index(p: Path) -> int:
        # model.cfr.<n>.data
        return int(p.name.rsplit(".", 2)[1])

    return [canonical(p) for p in sorted(model_dir.glob("*.data"), key=index)]


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__ or "usage: compare-alloy-baselines.py OLD NEW", file=sys.stderr)
        return 2
    old_root, new_root = Path(sys.argv[1]), Path(sys.argv[2])
    models = sorted(
        {p.name for p in old_root.iterdir() if p.is_dir()}
        | {p.name for p in new_root.iterdir() if p.is_dir()}
    )
    if not models:
        print("error: no per-model directories found", file=sys.stderr)
        return 2

    failures = 0
    print(f"{'model':32} {'exit':>9} {'count':>11} set-equal order")
    for m in models:
        old_dir, new_dir = old_root / m, new_root / m
        if not old_dir.is_dir() or not new_dir.is_dir():
            print(f"{m:32} {'MISSING in ' + ('new' if not new_dir.is_dir() else 'old'):>9}")
            failures += 1
            continue
        exit_old = (old_dir / "exit.txt").read_text().strip()
        exit_new = (new_dir / "exit.txt").read_text().strip()
        inst_old, inst_new = instances(old_dir), instances(new_dir)
        set_equal = sorted(inst_old) == sorted(inst_new)
        order_equal = inst_old == inst_new
        exit_str = exit_old if exit_old == exit_new else f"{exit_old}->{exit_new}"
        count_str = (
            str(len(inst_old))
            if len(inst_old) == len(inst_new)
            else f"{len(inst_old)}->{len(inst_new)}"
        )
        order_str = "same" if order_equal else ("drift" if set_equal else "-")
        print(
            f"{m:32} {exit_str:>9} {count_str:>11} "
            f"{'yes' if set_equal else 'NO':>9} {order_str}"
        )
        if not set_equal or exit_old != exit_new:
            failures += 1

    print(
        f"\n{len(models)} models compared; "
        f"{failures} with exit or instance-set differences"
    )
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
