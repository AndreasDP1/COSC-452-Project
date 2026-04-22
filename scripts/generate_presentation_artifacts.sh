#!/usr/bin/env bash
# Regenerate all presentation-ready tables and figures. Run from repo root.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

QUICK="${1:-}"

if [[ "$QUICK" == "--quick" ]]; then
  echo "=== Quick presentation run (smaller GA) ==="
  clojure -M:pres -- --quick
else
  echo "=== Full presentation run (may take 8–15+ minutes) ==="
  clojure -M:pres
fi

echo "=== Evolution report (evo) ==="
clojure -M:evo

echo "=== Figures (requires: python3 -m pip install matplotlib) ==="
if python3 scripts/plot_presentation_figures.py; then
  echo "OK: see results/figures/ and results/PRESENTATION_INDEX.md"
else
  echo "Plot step failed — see Python traceback above."
  echo "If ImportError: matplotlib, run: python3 -m pip install matplotlib"
  exit 1
fi
