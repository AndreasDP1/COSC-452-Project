# Results narrative (paper-style)

**Purpose:** One place to read *what we did, what the numbers mean, what the figures show, and what is still open*.  **Regenerate artifacts first** (`./scripts/generate_presentation_artifacts.sh`), then refresh the short “Latest run” bullets below.

**Mechanics (commands, file paths):** see **`PRESENTATION_INDEX.md`**.  
**Core data files:** **`presentation_ga_runs.csv`**, **`presentation_hand_baseline.csv`**, **`presentation_5case_errors.csv`**, **`evolution_runs.csv`**.

---

## 1. Executive summary

This project optimizes a 7-role ER staffing genome under budget constraints using stochastic week-long simulation and GA search.  
In the final presentation run (`week-v1-pres` baseline with budget repair), feasible GA rows stayed within budget and separated by mean error, match rate, and cross-scenario queue/wait metrics.  
The ablation without budget repair produced the expected infeasible behavior (mean error near 10000).  
The 5-case run (`E`) optimizes across five worlds at once; scalar **mean error** can sit near other rows (here `0.245` vs `0.099`–`0.278` depending on pair), while **cross-scenario** wait and queue columns still show where the joint objective binds (`avg_wait_max`, `avg_queue_len_max` on `E`).  
Presentation and evolution pipelines use different baseline scenarios (`week-v1-pres` vs `week-v1`), so values should be cited with pipeline context.

---

## 2. Two result pipelines

| Pipeline | Command | Baseline scenario | Typical use |
|----------|---------|-------------------|-------------|
| **Presentation** | `clojure -M:pres` | `week-v1-pres` (+ real-hourly, random events, 5-case) | Slides, figures, stress-tuned tables |
| **Evolution report** | `clojure -M:evo` | `week-v1` (classic) | Alternate report, comparable to older experiments |

Same *code*, different default EDN for the first two cases in `:pres`. Mention which pipeline a number comes from.

---

## 3. Interpretation checklist

Final observations from the current generated files:

1. **Ablation behaves as expected:** `A-ablation-synthetic-no-budget-repair` has mean error `10000.097847` and `within_budget_first=false`, confirming the budget penalty is active.
2. **Feasible single-scenario (`A`, `D`):** both in budget. `A-ga-synthetic-only` shows a tiny non-zero first-scenario wait (`avg_wait_first` ~ `4.9e-6` h) with small queue columns; `D` still rounds to zero on wait/queue in the CSV but differs from `A` on mean error (`0.2046` vs `0.1764`) and match rate (~`0.49` vs ~`0.58`).
3. **2-case (`B` vs `C`):** tournament has lower mean error (`0.262226` vs `0.278583`); cross-scenario waits remain visible (`avg_wait_max` ~ `0.00899` vs `0.01613` h). Match rates both ~`0.43`.
4. **5-case (`E`):** mean error `0.245449`; cross-scenario stress shows in `avg_wait_max` ~ `0.0300` h and `avg_queue_len_max` ~ `0.926` (and time-in-system max ~ `0.415` h).
5. **5-case per-scenario errors (best genome):** largest on `week-v1-pres-real-hourly` (`0.405975`); others cluster near `0.13`–`0.23` (`presentation_5case_errors.csv`).
6. **Hand baseline:** on `week-v1-pres` / real-hourly variants, demo staffing still shows large waits (~`11.3` h / ~`19.2` h) under the same 50k cap — useful as an untuned reference, not an optimized policy.

---

## 4. Figures and interpretation

| Figure | What it shows | Suggested one-liner |
|--------|-----------------|--------------------------------------|
| `mean_error_log.png` | All runs on log scale | Ablation vs feasible methods on one axis |
| `mean_error_linear_no_ablation.png` | Feasible runs only, linear | Small gaps between B/C need zoom, not hype |
| `mean_error_zoom_feasible.png` | Zoomed feasible mean error | Lexicase vs tournament difference in context |
| `avg_wait_max.png` / `avg_wait_mean.png` | Cross-scenario waits | Use when “first scenario wait” is flat |
| `avg_queue_len_max.png` | Queue stress | Useful when avg wait columns look like 0 |
| `match_rate_first.png` | Specialty match (1st scenario) | Tradeoff vs wait/error |
| `hand_baseline_wait_by_scenario.png` | Demo staffing by world | Demand shape matters (esp. real-hourly) |
| `hand_baseline_fitness_by_scenario.png` | Same, fitness scalar | Connects to optimization objective |
| `lexicase_vs_tournament_2case.png` | B vs C | Side-by-side selection comparison |
| `synthetic_fixed_vs_random_events.png` | Fixed EDN events vs random | Robustness to shock *model* |
| `metrics_grid_4panel.png` | Four metrics at once | Overview slide |
| `five_case_per_scenario_errors.png` | Error per lexicase case | Where the 5-case genome struggles |

---

## 5. Canonical data files (by question)

| Question | File |
|----------|------|
| Narrative interpretation + talking points | `FINDINGS.md` |
| Machine-readable GA rows | `presentation_ga_runs.csv` |
| Hand staffing across scenarios | `presentation_hand_baseline.csv` |
| Evolution-only rows (classic `week-v1`) | `evolution_runs.csv` |
| 5-case per-scenario errors | `presentation_5case_errors.csv` |
| Figure interpretations | `FINDINGS.md` §4 |

---

## 6. Limitations (short)

- Stylized simulation; parameters are **tunable** (λ, caps, costs) — document the EDN we use when we cite numbers.
- **Budget repair** searches feasible staffing; “zero wait” on an easy scenario does not prove real-world optimality.
- Presentation vs evolution baselines differ — see §2.

Details are included in §3 and §4 of this file.

---

## 7. Open questions & future work

- [ ] Align **`evolution_run`** with **`week_scenario_presentation.edn`** if one baseline should be used everywhere.
- [ ] Run sensitivity sweeps on λ and budget cap to test ranking stability (`B` vs `C`, 2-case vs 5-case).
- [ ] Add percentile-based waiting metrics (for example, proportion waiting over threshold).
- [ ] Expand real-data validation beyond the current hourly-share profile.

---

## 8. Latest run

- Date: 2026-04-22
- Command: `./scripts/generate_presentation_artifacts.sh` (full `:pres` + `:evo` + `plot_presentation_figures.py`)
- Presentation scenario: `week_scenario_presentation.edn` (`week-v1-pres`: λ `30`/h, daily cap `50000`; evolution batch still uses classic `week_scenario.edn` / `week-v1`).
- Outputs refreshed: `presentation_ga_runs.csv`, `presentation_hand_baseline.csv`, `presentation_5case_errors.csv`, `PRESENTATION_BUNDLE.md`, `presentation_5case_detail.txt`, `evolution_runs.csv`, `evolution_report.md`, `lexicase_5case_detail.txt`, `results/figures/*.png`.

**Evolution batch (classic `week-v1`, same script run):** 5-case mean error `0.156033`; single-scenario and 2-case rows remain in the ~`0.099`–`0.101` band on mean error (`evolution_runs.csv`). Use this pipeline when comparing to older baseline tuning, not interchangeably with `week-v1-pres` presentation numbers without a note.
