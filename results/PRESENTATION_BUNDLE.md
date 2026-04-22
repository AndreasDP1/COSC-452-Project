# Presentation experiment bundle

_Full search budget — suitable for screenshots / final report._

## 1. How to read fitness

- Scalar **fitness** = weighted waits/queues + specialty mismatch + **10000** if daily cost exceeds that scenario’s **budget cap**.
- **Mean error** in GA rows = average of per-scenario errors (lexicase uses the full error vector).
- **Budget repair:** Each GA genome is adjusted with **`repair-staffing-budget`** so daily payroll ≤ the **minimum** cap across the scenarios in that run (when tight-budget is in the set, **48k** binds). Comparisons focus on **operational** quality, not runaway staffing.
- **Ablation row** (`*-no-budget-repair*`): same GA **without** repair — often **over budget** with fitness ~**10000** + small terms; use as **unconstrained vs constrained** contrast.
- **Wait columns:** **1st** = wait on the **first** scenario only (often **≈0** after budget repair — feasible staffing clears waits). **Max / mean** = over **all** scenarios in that run.
- **Queue / time-in-system** (second table + extra CSV columns): often **non-zero** even when average wait rounds to 0.

## 2. Hand-picked staffing (demo baseline)

Same staffing as `er-staffing.main` demo — not tuned by search.

| Label | Scenario | Daily cost / cap | In budget? | Avg wait | Match rate | Fitness |
|-------|----------|------------------|------------|----------|------------|---------|
| hand-picked (main demo) | week-v1-pres | 32088 / 50000 | yes | 11.3410 | 0.0822 | 51.5636 |
| hand-picked (main demo) | week-v1-pres-real-hourly | 32088 / 50000 | yes | 19.1591 | 0.0737 | 85.3581 |
| hand-picked (main demo) | week-severe-respiratory | 32088 / 120000 | yes | 0.0423 | 0.3694 | 0.3907 |
| hand-picked (main demo) | week-tight-budget | 32088 / 48000 | yes | 0.0014 | 0.5008 | 0.2030 |
| hand-picked (main demo) | week-high-demand | 32088 / 120000 | yes | 0.0285 | 0.4348 | 0.3318 |

## 3. Genetic algorithm comparisons

| Run | Scenarios | Mean error | Wait 1st | Wait max | Wait mean | Wait real-hr | Match (1st) | Budget (1st)? |
|-----|-----------|------------|----------|----------|-----------|--------------|---------------|----------------|
| A-ablation-synthetic-no-budget-repair | week-v1-pres (ablation) | 10000.097847 | 0.000000 | 0.000000 | 0.000000 | — | 0.8164 | false |
| A-ga-synthetic-only | week-v1-pres | 0.176382 | 0.000005 | 0.000005 | 0.000005 | — | 0.5769 | true |
| B-ga-lexicase-2-case | week-v1-pres+real-hourly | 0.278583 | 0.000000 | 0.016134 | 0.008067 | 0.016134 | 0.4308 | true |
| C-ga-tournament-2-case | week-v1-pres+real-hourly | 0.262226 | 0.000000 | 0.008991 | 0.004496 | 0.008991 | 0.4269 | true |
| D-ga-synthetic-random-events | week-v1-pres-rand-events | 0.204557 | 0.000000 | 0.000000 | 0.000000 | — | 0.4922 | true |
| E-ga-lexicase-5-case | 5-case lexicase | 0.245449 | 0.000000 | 0.030006 | 0.006001 | 0.030006 | 0.4273 | true |

### Same runs — queue length & time in system (max/mean over scenarios)

Use when **wait** columns look like 0 — constrained budgeting still allows queue / flow metrics to differ.

| Run | Queue len max | Queue len mean | Time-in-system max | Time-in-system mean |
|-----|---------------|----------------|---------------------|----------------------|
| A-ablation-synthetic-no-budget-repair | 0.000000 | 0.000000 | 0.207783 | 0.207783 |
| A-ga-synthetic-only | 0.000149 | 0.000149 | 0.282302 | 0.282302 |
| B-ga-lexicase-2-case | 0.501132 | 0.250566 | 0.391597 | 0.374716 |
| C-ga-tournament-2-case | 0.274154 | 0.137077 | 0.386485 | 0.370375 |
| D-ga-synthetic-random-events | 0.000000 | 0.000000 | 0.310459 | 0.310459 |
| E-ga-lexicase-5-case | 0.925780 | 0.185156 | 0.414805 | 0.344380 |

### 5-case lexicase — per-scenario errors (best genome)

| Scenario | Error |
|----------|-------|
| week-v1-pres | 0.231710 |
| week-v1-pres-real-hourly | 0.405975 |
| week-severe-respiratory | 0.131492 |
| week-tight-budget | 0.228798 |
| week-high-demand | 0.229270 |

## 4. Summary bullets

- **Budget-aware evolution:** Default runs use **repair** so rows compare **operational** scores on fundable staffing.
- **Ablation (`A-ablation-*`):** **No repair** — expect **~10000** mean error when the GA staffs up to cut waits; contrasts **fiscal feasibility** vs raw queue minimization.
- **2-case (synthetic + real hourly):** Compare **lexicase** vs **tournament** on **mean error** and **match rate** (`B` vs `C`).
- **5-case (`E`):** Harder joint objective than 2-case; mean error often **higher** than `B`/`C`.
- Chart from **`presentation_ga_runs.csv`** and hand baseline from **`presentation_hand_baseline.csv`**.
