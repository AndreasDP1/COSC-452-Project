# Complete project deep dive (canonical docs)

This is the primary team document.

This document is the **long, plain-language explanation** of the ER staffing evolution project: what problem we solve, how the code models it, what **genome / GA / lexicase / replications / events** mean, and **what each file and major function does**.

**Shorter navigation:** [`CODEMAP.md`](CODEMAP.md) · **Simulator math:** [`SIMULATION_MODEL.md`](SIMULATION_MODEL.md)

---

## Part A — The story

### A.1 Research question (plain English)

Hospitals staff emergency departments with **different kinds of people**: respiratory techs, cardiologists, radiologists, mental health specialists, plus **generalists** (physicians, PAs, nurses). Patients arrive with **different medical conditions**. Ideally a **specialist who “matches” the condition** treats the patient (faster, better match rate). If no matching specialist is free, a **generalist** can still treat them, sometimes at lower “effective speed.” If the waiting room is **overloaded**, the model may **force** a non-matching specialist to help anyway.

**Money is limited:** there is a **daily budget** for staffing. You cannot hire everyone.

**Surprises happen:** “events” (disasters, outbreaks) change **how many people arrive** and/or **what fraction have which condition** (e.g. more respiratory cases during a fire scenario).

**Evolutionary computation** asks: *what staffing mix (how many of each role) performs well **across** these situations?* And *how does the “best” staffing change when we optimize under different demand patterns (synthetic vs real-shaped arrivals)?*

### A.2 What the code actually optimizes

We do **not** optimize minute-by-minute schedules of named people. We use a **flat genome**:

**Seven integers** — same staffing **every day** of the simulated week:

| Gene (key) | Role | Typical idea |
|--------------|------|----------------|
| `:rt` | Respiratory therapist (specialist) | Good for respiratory conditions |
| `:cardio` | Cardiology-oriented specialist | Cardio conditions |
| `:radio` | Radiology / imaging-oriented | Trauma/imaging-heavy in our scenario |
| `:mh` | Mental health specialist | Psych conditions |
| `:physician` | General physician | Treats any condition at generalist speed |
| `:pa` | Physician assistant | Generalist |
| `:nurse` | Nurse | Generalist |

The **genetic algorithm** searches over these seven numbers (within **bounds** in `data/synthetic/v2/ga_config.edn`). For each candidate genome, we **run the simulation** and compute a **fitness score** (lower = better). Selection (including **lexicase**) decides which genomes become **parents** for the next generation.

---

## Part B — Vocabulary

### B.1 Genome

A **genome** here is a map of **seven integers**, e.g. `{:rt 2 :cardio 1 … :nurse 4}`. It is the **decision variable** the GA changes.

### B.2 Generation

One **generation** in the GA = one loop: evaluate everyone -> sort by quality -> keep elites -> breed children -> repeat. This is **not** “24 hours”; simulation time is separate.

### B.3 Mutation

**Mutation** randomly **re-rolls** some genes (each gene has a probability `mutation-rate`). New value is a random integer **inside the allowed min/max for that role** (`:genome-bounds` in `ga_config.edn`).

**Where:** `evolution/operators.clj` — `mutate`.

### B.4 Crossover

**Crossover** combines two parent genomes. We use **uniform crossover**: for each of the seven roles, flip a coin—take that count from parent A or parent B.

**Where:** `evolution/operators.clj` — `uniform-crossover`.

### B.5 Fitness (error)

**Fitness** = a **single number** we want to **minimize** (lower is better). It combines simulated outcomes: wait time, time in system, queue length, “specialty mismatch” (1 − match rate), and a **huge penalty** if daily staffing cost exceeds **daily budget**.

**Where:** `evaluation/fitness.clj` — `score-summary`.

### B.6 Case (for lexicase)

In **this** codebase there are **two different “case” ideas**:

1. **GA lexicase parent selection** (`evolution/selection.clj`): A **case** = **one scenario** (e.g. synthetic arrivals vs real-shaped hourly arrivals). Each individual gets a **vector of errors** `[e₁, e₂, …]`—one fitness value per scenario. Lexicase **shuffles** the order of cases and **filters** the population to those who are best on each case in turn.

2. **Helper `summary->case-errors`** (`evaluation/lexicase.clj`): Turns **one** simulation summary into **five** numbers (wait, time in system, queue, 1−match, budget bit). That is a **multi-objective breakdown** of a **single** run—useful for analysis; the main GA path uses **`case-errors`** in `evolution/evaluation.clj` (one scalar per **scenario**).

### B.7 Replications

**Stochastic** simulation: random arrivals (Poisson counts) and random service lengths (exponential). One run = **one random seed**. **Replications** = run the **same staffing** multiple times with different seeds and **average** the metrics so the score is **less noisy**.

**Where:** `run-week` in `simulation/stage_week.clj` calls `run-one-week` `replications` times; `metrics_week.clj` averages.

---

## Part C — The simulation

### C.1 Horizon

Default **168 hours** = one week of **continuous** simulation time (hours 0…167).

### C.2 Arrivals

For each hour `h`, we set a **Poisson mean** λ_h (expected number of arrivals that hour):

- **Synthetic baseline:** same λ every hour from `:base-arrival-rate-per-hour` (unless events multiply it).
- **Real-shaped:** `:hourly-arrival-rates` from hospital CSV **shares**—same **expected total arrivals per day** as synthetic, but **morning peaks** etc.

Then we sample **how many** arrivals `N ~ Poisson(λ_h)` and place each arrival **uniformly** inside `[h, h+1)`.

**Where:** `arrivals/piecewise-hourly-arrivals`, driven by `conditions/hourly-arrival-rates-vector`.

### C.3 Patient condition

When a patient arrives at time `t`, we sample a **condition** (e.g. `:respiratory`) from a **probability vector**. **Baseline** probabilities come from `:condition-probs-baseline`. **Active events** multiply selected categories and **renormalize** so probabilities still sum to 1.

**Where:** `conditions/condition-probs-at`, `conditions/sample-condition`, `conditions/merge-condition-probs`.

### C.4 Two different “multipliers”

**1) Service-rate multipliers** (`:rate-multipliers` in scenario EDN)—affect **how fast** a server works (higher rate -> shorter expected service time):

- `:specialist-match` (e.g. 1.5×) when specialist **treats** that condition.
- `:specialist-mismatch` (e.g. 0.5×) when specialist treats a condition **outside** their scope.
- `:physician`, `:pa`, `:nurse` multiply generalist base rates.

Applied in `routing/effective-service-rate`.

**2) Event multipliers on the scenario** (`:events` entries):

- `:condition-multipliers` — multiply **relative frequency** of condition categories while the event is active (then renormalize). Affects **who** arrives, not directly service speed.
- `:arrival-multiplier` — multiplies **overall arrival intensity** λ during that window (can be >1).

### C.5 Queue and routing

**FIFO** queue. When a server becomes free (or at arrivals), we try to start service for the **person at the front**.

**`choose-server`** (`routing.clj`):

1. **Idle matching specialist** first (fixed order among specialists: RT -> Cardio -> Radio -> MH).
2. Else **generalist** in order **Physician -> PA -> Nurse**.
3. If **forced** (queue length > threshold **or** head-of-line wait > threshold), allow **mismatched specialist** (still uses mismatch service-rate multiplier).

### C.6 Service time

Exponential distribution: **mean service time** = 1 / (effective service rate). Rate = **base-service-rate[role] × multiplier** from C.4.

**Where:** `random/sample-exponential` in `stage_week` drain loop.

### C.7 Metrics produced

Per **replication** and averaged:

- Average wait, average time in system, average queue length.
- **Specialty matching rate** = fraction of **completed** patients served as `:specialist-match`.
- Utilization by role.
- Budget check: **daily** staffing cost vs `:daily-budget-cap`.

---

## Part D — Events: fixed vs random

### D.1 Fixed events (EDN)

`data/synthetic/v2/week_scenario.edn` lists `:events` with `:start-hour`, `:duration`, `:condition-multipliers`, optional `:arrival-multiplier`. Deterministic: **same** schedule every evaluation unless we change the file.

### D.2 Random events (`:random-events`)

If the scenario contains `:random-events`, `random_events/realize` **creates** a new `:events` list before the run: random **start hour**, **duration** in a range, and picks a **template** (multipliers). **Seeded RNG** -> reproducible if we fix seeds. This **replaces** fixed `:events` for that scenario map.

**Where:** `simulation/random_events.clj` — `realize`; called at start of `run-one-week` in `stage_week.clj`.

---

## Part E — Evolution pipeline (GA)

### E.1 `evolution/evaluation.clj`

| Function | What it does |
|----------|----------------|
| `case-errors` | For each **scenario** in a list, runs `run-week`, then `fitness/score-summary`. Returns vector of scalars—**one error per scenario**. |
| `mean-error` | Average of a vector of errors. |

### E.2 `evolution/operators.clj`

| Function | What it does |
|----------|----------------|
| `random-staffing` | Builds a random genome respecting bounds. |
| `uniform-crossover` | Child inherits each gene from parent A or B with 50/50. |
| `mutate` | Each gene independently may jump to a new random value in bounds. |

### E.3 `evolution/selection.clj`

| Function | What it does |
|----------|----------------|
| `lexicase-pick` | Standard lexicase filtering on **error vector** (one entry per scenario). Shuffles case order; repeatedly keeps individuals tied for **minimum error** on the current case. |
| `lexicase-select-n-without-replacement` | Runs `lexicase-pick` repeatedly, each time **removing** the winner from the pool, until `n` individuals are chosen — this is **full environmental lexicase** (who survives into the next generation). |
| `tournament-pick` | Picks `k` random individuals; keeps the one with **lowest mean error** (alternative parent selection). |
| `pick-parent` | Dispatches `:lexicase` vs `:tournament`. |

### E.4 `evolution/ga.clj`

| Function | What it does |
|----------|----------------|
| `evaluate-all` | Maps each genome to `{:staffing :errors}`. |
| `breed-children` | Repeatedly: pick two parents (`pick-parent`), crossover with probability `crossover-rate`, mutate, repair empty staffing. |
| `next-population-standard` | Elites copied + `breed-children` for the rest (`:selection` = `:lexicase` or `:tournament` for **parents**). |
| `next-population-lexicase-full` | **`lexicase-select-n-without-replacement`** on the whole evaluated population, then light mutation — **no crossover**; this is the **full lexicase survival** mode. |
| `run-ga` | Full loop; `:selection :lexicase-full` uses environmental lexicase; otherwise parent-selection GA. |

**Two modes:** `:lexicase` = *parent* lexicase + crossover + mutation (typical “GA with lexicase selection”). `:lexicase-full` = *environmental* lexicase filling the entire next population, then mutation only.

### E.5 Batch experiment / reports

**`experiments/evolution_run.clj`** -> `-main` runs **five** GA configurations and writes
`results/evolution_report.md` and `results/evolution_runs.csv`.

**`experiments/presentation_run.clj`** -> `-main` runs the presentation bundle (A/B/C/D/E rows),
writes `results/PRESENTATION_BUNDLE.md`, `results/presentation_ga_runs.csv`,
`results/presentation_hand_baseline.csv`, `results/presentation_5case_errors.csv`, and
tries to generate figures.

**Commands:**

- `clojure -M:evo`
- `clojure -M:pres`
- `clojure -M:pres -- --quick`
- `./scripts/generate_presentation_artifacts.sh` (runs pres + evo + plot script)

---

## Part F — File-by-file reference (source tree)

### F.1 Scenario and data

| File | Role |
|------|------|
| `scenario_v2.clj` | `load-week-scenario` reads EDN; `load-week-scenario-with-real-hourly-profile` attaches CSV-based hourly λ. |
| `data/real_profile.clj` | Reads `hourly_arrival_profile_from_sample.csv`; builds `:hourly-arrival-rates`. |
| `data/synthetic/v2/week_scenario.edn` | Full week: providers, costs, baseline condition mix, routing thresholds, **fixed** events, budget. |
| `data/synthetic/v2/ga_config.edn` | **Gene bounds** and GA defaults for experiments. |

### F.2 Simulation core

| File | Role |
|------|------|
| `simulation/conditions.clj` | Which events active at time `t`; merged condition probs; `base-lambda-at-hour`; `hourly-arrival-rates-vector`; `sample-condition`. |
| `simulation/routing.clj` | `effective-service-rate`, `choose-server`. |
| `simulation/stage_week.clj` | **DES** main loop: `run-one-week`, `run-week`; calls `random_events/realize`; `drain-queue-at-time`. |
| `simulation/metrics_week.clj` | Means across replications. |
| `simulation/random_events.clj` | `realize` stochastic disaster windows. |

### F.3 Supporting utilities

| File | Role |
|------|------|
| `arrivals.clj` | `piecewise-hourly-arrivals` — Poisson counts + uniform times in hour. |
| `random.clj` | Seeded RNG, Poisson, exponential, shuffle, `rand-int-between`. |
| `staffing_v2.clj` | Expand genome to servers; **daily** cost; within budget. |

### F.4 Evaluation & evolution

| File | Role |
|------|------|
| `evaluation/fitness.clj` | `score-summary` — scalar fitness. |
| `evaluation/lexicase.clj` | `summary->case-errors` (5 metrics), `casewise-rank` helper. |
| `evolution/*.clj` | GA as in Part E. |

### F.5 Entry points

| File | Role |
|------|------|
| `main.clj` | Quick demo printout. |
| `experiments/evolution_run.clj` | Full comparison + markdown/CSV. |
| `experiments/presentation_run.clj` | Presentation bundle + CSV + optional figures. |

### F.6 Tests (`test/er_staffing/`)

| File | What it checks |
|------|----------------|
| `routing_test.clj` | Routing prefers matching specialist / generalist. |
| `stage_week_test.clj` | `run-week` returns expected keys. |
| `fitness_test.clj` | Over-budget staffing gets worse score. |
| `lexicase_test.clj` | `casewise-rank` behavior. |
| `evolution_test.clj` | Operators + small `run-ga`. |
| `real_profile_test.clj` | CSV shares -> 168 λ vector. |
| `runner.clj` | Runs all test namespaces. |

---

## Part H — Commands cheat sheet

```bash
clojure -M:test
clojure -M -m er-staffing.main
clojure -M:evo
clojure -M:pres
clojure -M:pres -- --quick
./scripts/generate_presentation_artifacts.sh --quick
python3 scripts/plot_presentation_figures.py
python3 scripts/preprocess_real_data.py   # refresh CSVs from Excel sample
```

---

## Appendix A — Practical runbook and I/O reference

### A.1 Key commands

| Command | What it does |
|---------|---------------|
| `clojure -M:test` | Run all tests. |
| `clojure -M -m er-staffing.main` | Quick single-staffing demo run. |
| `clojure -M:evo` | Run evolution experiment batch; writes `results/evolution_report.md` and `results/evolution_runs.csv`. |
| `clojure -M:pres` | Run presentation batch; writes presentation CSV/markdown outputs. |
| `clojure -M:pres -- --quick` | Smaller, faster presentation run. |
| `./scripts/generate_presentation_artifacts.sh` | One command for `:pres`, `:evo`, and figures. |
| `python3 scripts/preprocess_real_data.py` | Refresh processed real hourly profile CSV from source sample. |
| `python3 scripts/plot_presentation_figures.py` | Regenerate PNG figures from current CSV outputs. |

### A.2 Primary scenario input (EDN)

Main file: `data/synthetic/v2/week_scenario.edn`

Common keys:

- `:horizon-hours`
- `:base-arrival-rate-per-hour`
- `:condition-probs-baseline`
- `:routing` (`:queue-threshold`, `:wait-threshold-hours`)
- `:daily-budget-cap`
- `:providers`
- `:rate-multipliers`
- `:events`
- optional `:hourly-arrival-rates`
- optional `:random-events`

### A.3 Primary simulation output (`run-week`)

`(week/run-week scenario staffing {:replications n :seed s})` returns summary keys such as:

- `:avg-wait-time`
- `:avg-time-in-system`
- `:avg-queue-length`
- `:specialty-matching-rate`
- `:daily-staffing-cost`
- `:within-budget?`
- `:patients-arrived`
- `:patients-completed`
- `:utilization-by-role`
