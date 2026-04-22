# Evolution (implemented)

- **`ga.clj`** — `run-ga`: population, elitism, crossover, mutation, lexicase or tournament parents.
- **`operators.clj`** — Uniform crossover, per-gene mutation within bounds.
- **`selection.clj`** — `lexicase-pick`, `tournament-pick`.
- **`evaluation.clj`** — `case-errors`: runs `run-week` per scenario, returns fitness scalars.

Bounds and GA hyperparameters: `data/synthetic/v2/ga_config.edn`.

Batch experiment (synthetic vs real hourly vs lexicase vs random events): `clojure -M:evo` -> `results/`.

See **`docs/CODEMAP.md`**.
