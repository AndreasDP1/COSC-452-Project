# Experiments

| Main | Command |
|------|---------|
| GA comparison (synthetic vs real hourly vs lexicase vs random events) | `clojure -M:evo` |
| **Presentation bundle** (hand baseline + GA variants + CSVs) | `clojure -M:pres` — add `--quick` for a shorter smoke run |

Writes `results/evolution_report.md` and `results/evolution_runs.csv`.

`clojure -M:pres` writes `results/PRESENTATION_BUNDLE.md`, `presentation_hand_baseline.csv`, `presentation_ga_runs.csv`.

See `evolution_run.clj`, `presentation_run.clj`, and `docs/CODEMAP.md`.
