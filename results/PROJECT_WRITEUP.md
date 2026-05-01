# ER Staffing Project Writeup

## Background
Emergency department staffing is difficult because demand changes by hour, patient condition mix changes over time, and budgets are limited. Our project models this as a simulation + optimization problem: for a given scenario, find a 7-role staffing plan that performs well while staying budget-feasible.

## Aims
- Build a week-long ER simulation with realistic stochastic arrivals and condition-based routing.
- Optimize a 7-integer staffing genome (`rt`, `cardio`, `radio`, `mh`, `physician`, `pa`, `nurse`) using a genetic algorithm.
- Compare parent-selection strategies (lexicase and tournament) across multiple scenario sets.
- Train a surrogate ML model that predicts near-optimal staffing directly from scenario features, both to explore patterns and so users (i.e in hospitals) do not need to run full GA every time.

## Process
We implemented a discrete-event simulation over a 168-hour horizon with queue dynamics, specialist/generalist routing, event shocks, and daily budget constraints. We then integrated a GA pipeline with mutation, crossover, and selection variants, including a no-budget-repair ablation for comparison (fixing staffing so it stays under budget and produce optimal solution).  

Before building the ML model, we ran structured GA comparisons across scenario sets (single-case, 2-case, 5-case), and compared lexicase vs tournament on a matched 2-case setup with plots and recorded metrics.

After that, we generated from a script a large amount of varied scenarios and ran them against the simulation and GA to get the optimal genome and create a huge training dataset. Then trained a multi-output random-forest surrogate model.

## Results
- Simulation + GA pipeline produced feasible staffing solutions across diverse scenarios.
- Pre-model comparison results showed:
  - no-budget-repair ablation produced infeasible behavior with very high penalized error (`10000.0978`) and `within_budget_first=false`,
  - in the 2-case comparison, tournament outperformed lexicase on both mean error (`0.2622` vs `0.2786`) and max wait (`0.0090` vs `0.0161`),
  - 5-case lexicase was feasible but showed higher cross-scenario stress (`avg_wait_max=0.0300`, `avg_queue_len_max=0.9258`) with mean error `0.2454`,
  - in the evolution batch, 5-case error (`0.1560`) was clearly harder than single/2-case rows (roughly `0.0986`-`0.1014`).
- Surrogate model was trained with an 80/20 split (160 train, 40 test).
- Latest model metrics: mean MAE across roles `2.2679` staff, with role MAE values ranging from `0.825` (physician) to `4.975` (nurse).
- Per-role exact accuracies were strongest on physician (`0.50`) and cardio (`0.325`).
- We also implemented a live demo path: scenario input -> feature conversion -> model-predicted genome.

## Summary
The project achieved the intended end-to-end workflow: simulation, GA optimization, dataset generation, and fast surrogate prediction. While surrogate accuracy can still improve with more data/model tuning, the current system demonstrates a practical proof of concept for rapid staffing recommendation from scenario inputs and can be expanded and used in for different scenarios and hospitals (i.e just by adding more features in the json scenario and altering the simulation/ga slightly).
