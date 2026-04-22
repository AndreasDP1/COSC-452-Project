# Code map (navigation)

Primary team doc: [`COMPLETE_PROJECT_DEEP_DIVE.md`](COMPLETE_PROJECT_DEEP_DIVE.md). Use this file as a quick path index.

## Entry points

| Command | Namespace |
|---------|-----------|
| `clojure -M -m er-staffing.main` | Quick demo: one staffing, `run-week`, fitness |
| `clojure -M:evo` | GA comparison + writes `results/evolution_report.md` and `results/evolution_runs.csv` |
| `clojure -M:pres` | Presentation bundle + writes `results/presentation_*.csv` |
| `./scripts/generate_presentation_artifacts.sh` | Runs `:pres`, `:evo`, and plot generation |
| `clojure -M:test` | All tests |

## Source layout (`src/er_staffing/`)

| Path | Purpose |
|------|---------|
| `scenario_v2.clj` | Load EDN; `load-week-scenario-with-real-hourly-profile` merges hospital CSV shape |
| `data/real_profile.clj` | Parse `hourly_arrival_profile_from_sample.csv` -> `:hourly-arrival-rates` |
| `staffing_v2.clj` | 7-gene genome helpers, daily cost |
| `arrivals.clj` | Piecewise hourly Poisson arrivals |
| `random.clj` | Seeded RNG, Poisson, exponential, shuffle |
| `simulation/conditions.clj` | Hourly λ (constant or vector), events, condition sampling |
| `simulation/random_events.clj` | Expand `:random-events` spec -> concrete `:events` each run |
| `simulation/routing.clj` | `choose-server`, effective rates |
| `simulation/stage_week.clj` | DES `run-one-week`, `run-week` |
| `simulation/metrics_week.clj` | Replication means |
| `evaluation/fitness.clj` | Scalar fitness for GA / lexicase cases |
| `evaluation/lexicase.clj` | Metric-vector helpers (optional) |
| `evolution/evaluation.clj` | `case-errors`: one fitness scalar per scenario |
| `evolution/operators.clj` | Random genome, crossover, mutation |
| `evolution/selection.clj` | Lexicase + tournament parent pick |
| `evolution/ga.clj` | Full GA loop |
| `experiments/evolution_run.clj` | Compare synthetic vs real-hourly vs lexicase vs random events |
| `experiments/presentation_run.clj` | Presentation experiment bundle |
| `main.clj` | CLI demo |

## Data

| Path | Purpose |
|------|---------|
| `data/synthetic/v2/week_scenario.edn` | Default week + fixed disaster windows |
| `data/synthetic/v2/ga_config.edn` | Gene bounds + GA defaults |
| `data/processed/real/hourly_arrival_profile_from_sample.csv` | From `scripts/preprocess_real_data.py` |

## Results (generated)

| Path | Purpose |
|------|---------|
| `results/evolution_report.md` | Markdown table: runs, errors, metrics |
| `results/evolution_runs.csv` | Same data in CSV form |
| `results/presentation_ga_runs.csv` | Presentation GA rows for charts |
| `results/presentation_hand_baseline.csv` | Hand baseline rows across scenarios |
| `results/presentation_5case_errors.csv` | Per-scenario 5-case errors |
| `results/figures/*.png` | Generated figures from `scripts/plot_presentation_figures.py` |

See also [`COMPLETE_PROJECT_DEEP_DIVE.md`](COMPLETE_PROJECT_DEEP_DIVE.md) and [`SIMULATION_MODEL.md`](SIMULATION_MODEL.md).
