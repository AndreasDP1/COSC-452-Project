# ER triage staffing simulation

Discrete-event simulation of an ED triage subsystem (one queue, nurses + PIT as servers) with time-varying arrivals and stochastic service times. Run staffing experiments and export CSV result tables (wait time, time in system, queue length, utilization, idle time, cost, budget feasibility).

## Start here

**Read [`docs/project_instructions.md`](docs/project_instructions.md)** for the model spec, file map, data description (real-derived + synthetic), and the exact commands to run.

## Quick start

**Clojure** (needs JDK + [Clojure CLI](https://clojure.org/guides/install_clojure)):

```bash
clojure -M:test
clojure -M -m er-staffing.main
clojure -M -m er-staffing.experiments.baseline-grid
```

If `clojure -M:test` does not run tests (e.g. opens a REPL), try `clojure -M:run-tests`.

**Python** (preprocess workbook → CSV/JSON):

```bash
python3 -m pip install -r requirements.txt
python3 scripts/preprocess_real_data.py
```

## Core question

If triage has a **fixed staffing budget**, how should the department allocate **triage nurses** and **physicians-in-triage** so patients are seen sooner, without overspending or pointless idle time—under **time-varying arrivals**, **random service times**, and **multiple objectives**?

## Repo map (abbrev.)

| Path | Contents |
|------|----------|
| `src/er_staffing/` | Simulation (Stage A), metrics, fitness, experiments |
| `test/` | Tests + `runner.clj` for `clojure -M:test` |
| `data/synthetic/` | EDN scenarios and arrival profiles |
| `data/processed/real/` | Outputs from `scripts/preprocess_real_data.py` |
| `results/placeholders/` | e.g. `base_grid_results.csv` from baseline grid |
| `docs/` | `project_instructions.md` (single consolidated spec) |
