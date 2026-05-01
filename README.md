# ER Staffing Simulation + Evolutionary Optimization

This project models ER staffing as a week-long discrete-event simulation and optimizes a 7-role staffing genome using a genetic algorithm (lexicase or tournament selection). It also includes a surrogate-model workflow that learns scenario -> genome mappings from GA-labeled data.

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
- Python 3 (for preprocessing/plot scripts and model scripts)
- Python packages in `requirements.txt` (`pandas`, `openpyxl`, `scikit-learn`, `joblib`)

Run from repository root:

```bash
python3 -m pip install -r requirements.txt
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
- `clojure -M:dataset -- --count N ...`: generate many scenario + GA-best rows for model training
- `python3 scripts/preprocess_real_data.py`: refresh real hourly profile CSV
- `python3 scripts/plot_presentation_figures.py`: regenerate figures from CSV results
- `python3 scripts/train_genome_model.py`: train surrogate model from merged dataset CSV
- `python3 scripts/predict_genome.py --features-json <path>`: predict 7-role genome from one feature JSON
- `clojure -M -m er-staffing.experiments.scenario-features <scenario.edn> <out.json>`: convert scenario EDN to model feature JSON

## Workflow Paths

### 1) Presentation / Comparison Path (pre-model)

```bash
clojure -M:pres
clojure -M:evo
python3 scripts/plot_presentation_figures.py
```

Use this path for:
- selection comparisons (lexicase vs tournament)
- ablation behavior (budget repair off)
- 1/2/5-case result tables and figures

### 2) Dataset Generation Path (for surrogate model)

Generate GA-labeled rows:

```bash
clojure -M:dataset -- --count 30 --replications 5 --population 16 --generations 8 --seed 42 --out-csv results/model_training_dataset_p1.csv --scenario-dir data/synthetic/v2/model_dataset_p1
```

### 3) Model Training + Inference Path

Train:

```bash
python3 scripts/train_genome_model.py --input-csv results/model_training_dataset_all.csv
```

Infer from a scenario EDN (live demo flow):

```bash
clojure -M -m er-staffing.experiments.scenario-features data/synthetic/v2/model_dataset/scenario_0094.edn /tmp/scenario_features.json
python3 scripts/predict_genome.py --features-json /tmp/scenario_features.json
```

## Outputs

Generated outputs are written under `results/`, including:

- `results/evolution_runs.csv`
- `results/presentation_ga_runs.csv`
- `results/presentation_hand_baseline.csv`
- `results/presentation_5case_errors.csv`
- `results/figures/*.png`
- `results/FINDINGS.md`
- `results/model_training_dataset_all.csv`
- `results/models/genome_predictor.joblib`
- `results/models/genome_predictor_metrics.json`
- `results/PROJECT_WRITEUP.md`
- `results/COMPARISON_RESULTS.md`

## Documentation

- `docs/COMPLETE_PROJECT_DEEP_DIVE.md` (primary walkthrough)
- `docs/SIMULATION_MODEL.md` (simulation mechanics)
- `docs/CODEMAP.md` (file map)
