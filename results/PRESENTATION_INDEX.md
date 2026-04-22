# Presentation outputs — where everything is & how to regenerate

## How scenarios are selected

- **`clojure -M:pres`** runs the full presentation batch in one go (ablation, synthetic-only, 2-case lexicase vs tournament, random-events, 5-case). Which worlds run is fixed in **`src/er_staffing/experiments/presentation_run.clj`** (not per-run CLI flags).
- **`clojure -M:evo`** runs the five evolution-report experiments; see **`src/er_staffing/experiments/evolution_run.clj`**.

Extra worlds: add another `run-ga-experiment` line in that file rather than swapping EDN paths by hand for each run.

## One-shot regeneration (tables + plots)

From the **repository root**:

```bash
chmod +x scripts/generate_presentation_artifacts.sh
./scripts/generate_presentation_artifacts.sh
```

Or step by step:

```bash
clojure -M:pres          # full run (~8–15 min): CSVs (+ optional markdown snapshot)
clojure -M:evo           # evolution_runs.csv
python3 -m pip install matplotlib   # once
python3 scripts/plot_presentation_figures.py
```

Faster iteration (smaller GA):

```bash
clojure -M:pres -- --quick
python3 scripts/plot_presentation_figures.py
```

## Generated tables (commit with the repo or attach to a report)

| File | Contents |
|------|----------|
| `presentation_ga_runs.csv` | All GA rows: mean error, waits (1st / max / mean / real-hr), match rate, genome |
| `presentation_hand_baseline.csv` | Demo staffing across **five** scenario ids (waits, fitness) |
| `presentation_5case_errors.csv` | Per-scenario errors for the 5-case best genome |
| `evolution_runs.csv` | Evolution pipeline GA rows |

## Figures (`results/figures/`)

After `plot_presentation_figures.py`, `results/figures/` should contain PNGs such as:

| PNG | What it shows |
|-----|----------------|
| `mean_error_log.png` | All GA rows, **log** scale (ablation ~1e4 vs ~0.1) |
| `mean_error_linear_no_ablation.png` | Same without ablation — compare methods |
| `avg_queue_len_max.png` | **Preferred when waits look 0** — max avg queue length across scenarios |
| `avg_wait_max.png` | Max avg wait across scenarios in each run |
| `avg_wait_mean.png` | Mean of per-scenario waits |
| `match_rate_first.png` | Specialty match on first scenario |
| `hand_baseline_wait_by_scenario.png` | **Hand-picked** staffing: wait **varies** by scenario (shows non-trivial waits) |
| `hand_baseline_fitness_by_scenario.png` | Same, fitness scalar |
| `lexicase_vs_tournament_2case.png` | **B vs C** side-by-side (mean error + match rate) |
| `synthetic_fixed_vs_random_events.png` | **A** (fixed 6 EDN events) vs **D** (5 random shocks) |
| `metrics_grid_4panel.png` | Small multiples: mean error, wait max, wait mean, match rate |
| `mean_error_zoom_feasible.png` | **Zoomed** mean error (feasible runs) — small gaps become visible |
| `five_case_per_scenario_errors.png` | 5-case best genome: error **per scenario** (spread is real) |

If a PNG is missing, run `python3 scripts/plot_presentation_figures.py` again after refreshing the CSVs.

**Interpretation notes:** see **`FINDINGS.md`** (waits ~0 on easy cases, ablation ~10000, small B vs C gaps).

## Suggested flow

1. Problem + model (talking points from `FINDINGS.md`).
2. Hand baseline figure -> **`hand_baseline_wait_by_scenario.png`** (real-hourly wait higher than baseline).
3. GA overview -> **`mean_error_log.png`** + **`mean_error_linear_no_ablation.png`**.
4. 2-case selection -> **`lexicase_vs_tournament_2case.png`** (avoid over-interpreting tiny gaps).
5. Random events -> **`synthetic_fixed_vs_random_events.png`**.
6. 5-case stress -> **`avg_wait_max.png`** or **`metrics_grid_4panel.png`**.

Scenario/run mapping is summarized in **`FINDINGS.md`**.
