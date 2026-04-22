# ER Staffing Simulation + Evolutionary Optimization

This project models ER staffing as a week-long discrete-event simulation and optimizes a 7-role staffing genome using a genetic algorithm (lexicase or tournament selection).

Core model pieces:

- Patient arrivals by hour (synthetic or real-data-shaped profile)
- Condition-dependent routing (specialist-first with overload fallback)
- Event windows that shift demand composition/intensity
- Daily staffing budget constraints
- GA search over role counts (`rt`, `cardio`, `radio`, `mh`, `physician`, `pa`, `nurse`)

## Quick Start

Requirements:

- JDK 11+
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- Python 3 (for preprocessing/plot scripts)

Run from repository root:

```bash
clojure -M:test
clojure -M -m er-staffing.main
./scripts/generate_presentation_artifacts.sh --quick
```

For full outputs (longer runtime):

```bash
./scripts/generate_presentation_artifacts.sh
```

## Main Commands

- `clojure -M:test`: run test suite
- `clojure -M -m er-staffing.main`: single demo run
- `clojure -M:evo`: evolution report batch
- `clojure -M:pres`: presentation report batch
- `clojure -M:pres -- --quick`: faster presentation batch
- `python3 scripts/preprocess_real_data.py`: refresh real hourly profile CSV
- `python3 scripts/plot_presentation_figures.py`: regenerate figures from CSV results

## Outputs

Generated outputs are written under `results/`, including:

- `results/evolution_runs.csv`
- `results/presentation_ga_runs.csv`
- `results/presentation_hand_baseline.csv`
- `results/presentation_5case_errors.csv`
- `results/figures/*.png`
- `results/FINDINGS.md`

## Documentation

- `docs/COMPLETE_PROJECT_DEEP_DIVE.md` (primary walkthrough)
- `docs/SIMULATION_MODEL.md` (simulation mechanics)
- `docs/CODEMAP.md` (file map)
