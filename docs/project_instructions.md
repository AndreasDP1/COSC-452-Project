# Project instructions — ER triage staffing

## 1. Problem statement 
For a given triage staffing plan, answer:

> If triage is staffed with \(X\) nurses and \(Y\) physicians-in-triage (PIT) during a shift, and patients arrive according to a realistic time-varying arrival pattern, what happens to wait time, time in system, queue length, idle time, utilization, staffing cost, and budget feasibility?

This repository treats simulation as the evaluation engine for optimization. The search algorithm is a separate layer; the simulator and scoring code define what “good staffing” means under explicit assumptions.

The baseline implementation is **Stage A**: one queue and multiple servers, where nurses and PIT physicians both serve patients from the same queue. Arrivals vary by hour, service times are random, and the simulation returns average wait time, time in system, queue length, idle time, utilization, cost, and whether the staffing plan stays within budget.

The missing pieces are the larger-genome evolutionary search layer, full lexicase selection, and the more realistic Stage B nurse-then-PIT routing model.

---

## 2. Modeling scope and boundaries

### 2.1 What is modeled

- **Triage subsystem only**.
- A single triage service system with:
  - one waiting queue (FIFO),
  - multiple servers,
  - two server roles: **triage nurses** and **PIT physicians**.
- **Time-varying arrivals** during one shift horizon.
- **Stochastic service durations** that depend on server role.
- **Multiple replications** per staffing plan.

## 3. Simulation model: Stage A (baseline)

Stage A is a one-queue, multi-server model where **nurses and PIT share the same queue** and both can serve patients.

### 3.1 Inputs

#### Scenario map (EDN or in-memory)

A scenario is a Clojure map containing at least:

- `:scenario-id`
- `:shift-length-hours`
- `:hourly-arrival-rates` (vector, length = shift length; piecewise-constant hourly rate)
- `:nurse-service-rate-per-hour`
- `:pit-service-rate-per-hour`
- `:nurse-hourly-cost`
- `:pit-hourly-cost`
- `:budget-cap-per-shift`

Optional (documented but not currently central in code):

- `:time-unit` (defaults to hours)
- `:warmup-hours` (defaults to 0 for fixed-shift runs)

#### Staffing map

- `:nurses` (integer \(\ge 0\))
- `:pit-physicians` (integer \(\ge 0\))

Constraint enforced in code: total servers must be \(\ge 1\).

#### Simulation-control map

- `:replications`
- `:seed`
- `:record-event-log?` (optional)

### 3.2 Arrival generation

Arrivals are generated from a piecewise hourly profile:

- For each hour \(h\), sample \(N_h \sim \text{Poisson}(\lambda_h)\).
- Place those \(N_h\) arrivals uniformly in \([h, h+1)\).
- Combine and sort all arrival times.

This is a simple, implementation-friendly approximation to a non-homogeneous Poisson process with hourly constant intensity.

### 3.3 Service generation

When a patient begins service at time \(t\):

- Identify the server’s role (nurse or PIT).
- Sample a service duration from an **exponential distribution** with role-specific rate (per hour).
- Service completion time is \(t + \text{duration}\).

This is a baseline assumption chosen for simplicity and reproducibility; it is not claimed to be the definitive service-time distribution.

### 3.4 Event logic (discrete-event simulation)

Events:

- patient arrival,
- service start,
- service completion.

Core rules:

1. **Arrival:** if any server is idle, the arriving patient starts service immediately; otherwise the patient joins the waiting queue.
2. **Completion:** the patient exits the modeled subsystem; the server becomes idle; if the queue is non-empty, the next queued patient starts service immediately on the freed server.

The simulation tracks queue-length area for time-weighted average queue length, per-role busy time for utilization, and per-patient timing for wait and time in system.

### 3.5 Outputs

The canonical summary map (averaged across replications) follows the schema below.

```clojure
{:scenario-id "base-8h"
 :staffing {:nurses 3 :pit-physicians 1}
 :replications 20
 :avg-wait-time 0.42
 :avg-time-in-system 0.78
 :avg-queue-length 1.73
 :avg-total-idle-time 6.40
 :nurse-utilization 0.81
 :pit-utilization 0.66
 :patients-arrived 97
 :patients-completed 97
 :staffing-cost 1800.0
 :within-budget? true}
```

**Units:** simulation time is in **hours** internally. Budget is per shift.

Definitions:

- **Wait time:** service start time − arrival time.
- **Time in system:** service completion time − arrival time.
- **Average queue length:** time-weighted average queue length over the shift horizon.
- **Utilization:** busy time / available time for that role (bounded in \([0,1]\) by construction).
- **Idle time:** available time − busy time.

### 3.6 Replications

Because the model is stochastic, evaluation uses multiple replications.

Typical ranges:

- debug runs: ~5
- baseline tables: ~20
- final reported results: 30+

### 3.7 Validation expectations

These monotonic/qualitative checks should hold in most scenarios:

- low load + many staff -> low wait, high idle time
- high load + few staff -> high wait, low idle time, high utilization
- increasing staffing should typically reduce delay
- increasing staffing increases cost

---

## 4. Metrics and scoring

### 4.1 Metrics to report

At minimum:

- `:avg-wait-time`
- `:avg-time-in-system`
- `:avg-queue-length`
- `:avg-total-idle-time` (reported as an average per-server figure in the current implementation)
- `:nurse-utilization`, `:pit-utilization`
- `:staffing-cost`
- completed patient count

The project explicitly avoids single-metric optimization because it leads to perverse outcomes (e.g., minimizing cost only -> understaffing; minimizing wait only -> overstaffing).

### 4.2 Weighted-sum fitness

The baseline fitness is a transparent weighted sum:

- weighted wait time,
- weighted time in system,
- weighted idle time,
- plus a large penalty if the plan is over budget.

This is intended as a debuggable baseline.

### 4.3 Case-vector evaluation (helpers implemented; selection planned)

Case vectors are produced so that later work can compare weighted-sum evaluation to case-wise selection approaches.

Examples of cases:

- metrics as separate cases (wait, time in system, idle),
- budget feasibility as a case,
- multiple scenario conditions (low demand, high demand, low budget, peak profile).

The codebase currently includes helpers to construct case-error vectors and to perform basic diagnostics; full population-based lexicase selection is a planned extension.

---

## 5. Data: real-derived and synthetic

Two data streams are used by design:

1. **Real data (or proxy data)**: primarily for timestamp handling, arrival-profile extraction, and workflow demonstration.
2. **Synthetic, literature-informed scenarios**: for controlled, repeatable experiments and stress testing.

### 5.1 Real data (workbook) and processed artifacts

Raw input:

- `data/raw/real/hospital_data_sampleee.xlsx`

Processed outputs (generated by `scripts/preprocess_real_data.py`):

- `data/processed/real/hospital_wait_sample_cleaned.csv`
  - “micro” table: one row per record/visit with cleaned timestamps and derived waits.
- `data/processed/real/hourly_arrival_profile_from_sample.csv`
  - 24-row table (hour 0–23): arrival counts and shares pooled across all days.
- `data/processed/real/daily_hourly_arrivals_from_sample.csv`
  - “macro” table: one row per (calendar day, hour) with an arrival count.
- `data/processed/real/hospital_wait_sample_summary.json`
  - aggregate summary statistics used for quick sanity checks.

Row-count note:

- The cleaned CSV has tens of thousands of rows because it is per-record.
- The daily×hourly table is much smaller (hundreds of rows) because it is per (date, hour) bucket, not per record.

### 5.2 Synthetic scenarios

Synthetic scenarios exist as EDN files under:

- `data/synthetic/scenarios/`
- `data/synthetic/arrival_profiles/`

They provide controlled “demand shapes” and budget/service assumptions and are used for baseline experiments, sensitivity analyses, and comparisons.

### 5.3 Literature reference table

- `data/processed/literature/key_facts_table.csv` records parameter facts and provenance for report writing.

---

## 6. Experiments (what can be run)

The experiments are designed to produce baseline tables and sanity evidence before any evolutionary search.

### 6.1 Sanity scenarios

Goal: confirm qualitative behavior across low/medium/high load and staffing.

Expected:

- wait increases with load,
- idle time decreases with load,
- utilization increases with load.

### 6.2 Staffing grid baseline (brute force)

Goal: create a non-EC reference table, debug the simulator, and show what a small search space yields.

Method:

- brute-force all staffing pairs in a small range,
- record metrics + weighted fitness,
- identify best staffing per scenario.

Implementation:

- `src/er_staffing/experiments/baseline_grid.clj`
- output: `results/placeholders/base_grid_results.csv`

### 6.3 Budget sweep

Goal: understand budget tradeoffs.

Method:

- hold scenario constant,
- vary `:budget-cap-per-shift`,
- record best feasible staffing and outcomes.

### 6.4 Arrival-profile sensitivity

Goal: test dependence on demand shape.

Implementation example:

- `src/er_staffing/experiments/sensitivity.clj`

### 6.5 Weighted-score vs case-vector diagnostics

Goal: prepare for later lexicase comparisons.

Method:

- compare staffing plans under weighted aggregate score vs case-vector errors,
- identify generalists vs specialists.

### 6.6 Real-profile-inspired vs synthetic profiles

Goal: compare a profile extracted from the workbook to literature-shaped profiles.

Caution:

- intended as workflow and plausibility work, not a claim of ED census truth.

---

## 7. Repository layout and module responsibilities

### 7.1 Folder guide

- `src/er_staffing/` — Clojure simulation, metrics, evaluation, and experiments
- `test/` — Clojure tests + `runner.clj` test entrypoint
- `data/raw/` — original artifacts (workbook, paper PDFs/notes pointers)
- `data/processed/` — processed CSV/JSON outputs and literature tables
- `data/synthetic/` — scenario EDNs and synthetic arrival profiles
- `scripts/` — Python ETL script(s)
- `results/placeholders/` — experiment outputs (CSV tables)
- `docs/` — this document
- `notes/` — working notes
- `papers/` — papers
- `references/` — source map / provenance helpers

### 7.2 Key Clojure namespaces

| Namespace | File | What it does |
|----------|------|--------------|
| `er-staffing.main` | `src/er_staffing/main.clj` | Demo run: loads a scenario, runs a shift, prints summary + score. |
| `er-staffing.params` | `src/er_staffing/params.clj` | Scenario loading from EDN, staffing cost, budget check, server role expansion. |
| `er-staffing.random` | `src/er_staffing/random.clj` | RNG utilities; Poisson and exponential sampling. |
| `er-staffing.arrivals` | `src/er_staffing/arrivals.clj` | Piecewise hourly arrival generation. |
| `er-staffing.simulation.stage-a` | `src/er_staffing/simulation/stage_a.clj` | Stage A DES; replication runner; returns summary maps. |
| `er-staffing.simulation.metrics` | `src/er_staffing/simulation/metrics.clj` | Aggregates replication summaries by mean. |
| `er-staffing.evaluation.fitness` | `src/er_staffing/evaluation/fitness.clj` | Weighted-sum scoring with budget penalty. |
| `er-staffing.evaluation.lexicase` | `src/er_staffing/evaluation/lexicase.clj` | Case-error vector helpers and basic diagnostics. |
| `er-staffing.experiments.baseline-grid` | `src/er_staffing/experiments/baseline_grid.clj` | Brute-force staffing grid; writes a CSV result table. |
| `er-staffing.experiments.sensitivity` | `src/er_staffing/experiments/sensitivity.clj` | Demand scaling sensitivity experiment. |
| `er-staffing.data.real-dataset` | `src/er_staffing/data/real_dataset.clj` | Loads processed CSV hourly profile used by demo scenario. |
| `er-staffing.simulation.stage-b-placeholder` | `src/er_staffing/simulation/stage_b_placeholder.clj` | Notes only (planned extension). |

### 7.3 Python preprocessing

- `scripts/preprocess_real_data.py` reads the raw workbook and writes:
  - cleaned micro-level CSV,
  - 24-hour profile CSV,
  - daily×hourly arrival counts CSV,
  - JSON summary.

---

## 8. How to run (commands)

From the repository root:

### 8.1 Tests

```bash
clojure -M:test
```

If `clojure -M:test` opens a REPL instead of running tests, use:

```bash
clojure -M:run-tests
```

### 8.2 Demo run

```bash
clojure -M -m er-staffing.main
```

### 8.3 Baseline grid

```bash
clojure -M -m er-staffing.experiments.baseline-grid
```

The output is written to:

- `results/placeholders/base_grid_results.csv`

### 8.4 Other experiments

```bash
clojure -M -m er-staffing.experiments.sensitivity
clojure -M -m er-staffing.experiments.real-profile-demo
```

### 8.4 Python ETL

```bash
python3 -m pip install -r requirements.txt
python3 scripts/preprocess_real_data.py
```

---

## 9. Planned next implementations (roadmap)

### 9.1 Genome expansion (to make search non-trivial)

A staffing vector with only two integers \(`:nurses`, `:pit-physicians`\) is small enough that brute-force search is often a sufficient baseline.

Recommended genome options:

#### Option A: block staffing genome (recommended default)

Split a shift into 2-hour blocks and specify staffing counts per block.

For an 8-hour shift with four 2-hour blocks:

- `nurses_block_1`, `nurses_block_2`, `nurses_block_3`, `nurses_block_4`
- `pit_block_1`, `pit_block_2`, `pit_block_3`, `pit_block_4`

This yields 8 genes immediately and introduces meaningful time-varying tradeoffs.

#### Option B: block + float / on-call

Add:

- float nurse per block,
- on-call PIT indicator per block.

#### Option C: shift-template genome

Genes specify which template is active and how many staff follow each template (closer to scheduling).

### 9.2 Evolutionary algorithm layer

Implement:

- individual representation for the chosen genome,
- mutation and crossover operators,
- selection (tournament, truncation, etc.),
- fitness evaluation by calling the simulator,
- population loop and logging.

### 9.3 Lexicase (selection experiment, not a prerequisite)

Once the genome is larger and multiple scenarios are in play:

- construct case vectors across metrics and/or scenarios,
- implement lexicase selection and compare outcomes to weighted-sum selection.

### 9.4 Stage B simulation extension (optional)

Stage B can introduce:

- nurse-first triage,
- a routing fraction to PIT review,
- two queues or two sequential stages,
- stage-specific waiting times.

