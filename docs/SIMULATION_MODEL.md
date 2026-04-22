# Week staffing simulation — technical description

Primary team doc: [`COMPLETE_PROJECT_DEEP_DIVE.md`](COMPLETE_PROJECT_DEEP_DIVE.md). This file is a detailed simulation reference.

This document explains **how** the discrete-event simulation in `er-staffing.simulation.stage-week` behaves: time axis, arrivals, conditions, queue discipline, routing, service times, and reported statistics.

---

## 1. Modeling scope

- **One** FIFO waiting queue.
- **Multiple parallel servers** — one “person” per server; staffing counts are expanded into a list of servers with fixed role labels (`staffing_v2`).
- **No balking or abandonment** — every arriving patient eventually completes service or the simulation ends (in practice, with positive staffing, the queue drains).
- **Continuous time** in **hours** from `0` to `H` (default `H = 168`).

---

## 2. Time and events

The simulation maintains a **clock** and a sorted list of **future arrival times** (pre-generated). The next event is either:

- the **next arrival**, or  
- the **next service completion** (earliest `busy-until` among busy servers),

whichever occurs first. **Ties:** if an arrival and a departure share the same time, the code takes the **departure** branch first (`cond` requires `<` for arrivals to win), then the arrival at the next step.

Between events, nothing happens except that we may **drain the queue** at the current time (start services) after processing an arrival or a departure.

---

## 3. Arrivals

### 3.1 Hourly Poisson with varying λ

For each clock hour `h ∈ {0, …, H−1}`, the **baseline** Poisson mean `λ_h` is:

- **`:hourly-arrival-rates`** (optional, length = horizon): use `λ_h` from this vector — e.g. built from **empirical within-day shares** in `hourly_arrival_profile_from_sample.csv` while keeping the same **expected daily total** as `:base-arrival-rate-per-hour × 24`; or  
- otherwise the constant **`:base-arrival-rate-per-hour`** for every hour.

Then multiply by the product of **active events’** `:arrival-multiplier` values during hour `h` (typically `1.0`). So `λ_h` changes over time from demand shape **and** from events.

### 3.2 Drawing arrival times

`piecewise-hourly-arrivals` (in `arrivals.clj`) for each hour `h`:

1. Sample `N_h ~ Poisson(λ_h)`.
2. For each of the `N_h` patients, sample a time **uniformly** in `[h, h+1)`.

All times are collected and **sorted**. This is a standard piecewise-constant approximation to a non-homogeneous Poisson process.

---

## 4. Patient condition at arrival

When a patient arrives at time `t`:

1. **Active events** at `t` are those with `start-hour ≤ t < start-hour + duration`.
2. **Baseline** condition probabilities (`:condition-probs-baseline`) are **multiplicatively** bumped by each active event’s `:condition-multipliers` (per category), then **renormalized** to sum to 1.
3. A single category is drawn (inverse-CDF / cumulative walk in `sample-condition`).

So during a “respiratory spike” event, `:respiratory` gets a higher weight in the mixture even if total arrival rate is unchanged

---

## 5. Queue and routing

### 5.1 Queue

Patients in queue are maps `{:uid … :arrival … :condition …}`. **FIFO**: only the **head** is considered for assignment when servers free up or at an arrival event after joining the queue.

### 5.2 When can service start?

After each event at time `t`, the model calls **drain-queue-at-time**: while the queue is non-empty, try to assign the **head** to a server using `choose-server`.

### 5.3 `choose-server` (routing)

Inputs: scenario, current server states, patient **condition**, **wait** = `t − arrival`, **queue length** (patients waiting including head).

**Forced overload:**  
`forced? = (queue-len > queue-threshold) OR (wait > wait-threshold-hours)`.

**Idle servers** are those not busy.

**Selection order:**

1. If any idle **matching specialist** exists, pick by fixed specialist order **RT -> Cardio -> Radio -> MH** (first match wins). Service kind `:specialist-match`.
2. Else if any idle **generalist**, pick **Physician -> PA -> Nurse**. Kind `:generalist`.
3. Else if `forced?` and any idle **specialist** exists (same order), take first — patient is served by a **non-matching** specialist. Kind `:specialist-mismatch`.
4. Else **cannot start** — patient remains queued until a later event frees a server or thresholds change.

**Note:** A matching specialist who is busy does **not** block a generalist from taking the patient unless the rules above say otherwise: step 1 only checks **idle** matching specialists.

---

## 6. Service times

When service starts at time `t` for role `r` and patient condition `c`:

1. **Effective service rate** `μ` (patients per hour) =  
   `base-service-rate[r] × multiplier`  
   where multiplier follows slide rules:
   - specialist + condition in `:treats` -> `:specialist-match`
   - specialist + not in `:treats` -> `:specialist-mismatch`
   - generalist -> `:physician`, `:pa`, or `:nurse` multiplier

2. Service duration `D ~ Exponential(μ)` (mean `1/μ`), implemented as sampling with rate `μ`.

3. Completion time = `t + D`, capped for utilization: busy time accrues only up to horizon `H` (see `stage_week`).

The server becomes busy until completion; then it is idle and the queue is drained again.

---

## 7. Metrics at end of run (one replication)

- **Wait time** — recorded when service **starts** (time from arrival to service start). Averages are over **completed** patients.
- **Time in system** — departure minus arrival for completed patients.
- **Average queue length** — integral of queue size over `[0,H]` divided by `H`.
- **Specialty matching rate** — among completed patients, fraction where `service-kind` was `:specialist-match` (generalist and mismatch are excluded from the numerator).
- **Utilization by role** — sum of busy hours on servers with that role / (count of that role × `H`).

`run-week` runs several replications with different seeds and **averages** the summary scalars (`metrics_week`).

---

## 8. Budget

**Not** part of the dynamics: the simulator does not fire or hire mid-run. **Daily staffing cost** is computed from the genome and provider hourly rates:  

\(\text{daily cost} = 24 \times \sum_{\text{role}} n_{\text{role}} \times \text{hourly\_cost}_{\text{role}}\).

Compared to `:daily-budget-cap` for `:within-budget?` and for fitness penalties.

---

## 9. Random vs fixed disaster windows

- **Fixed:** `:events` in the EDN (deterministic start/duration/effects).
- **Random:** `:random-events` in the scenario map is expanded **once** at the start of each `run-one-week` into a new `:events` list (different seeds -> different times). This replaces fixed events for that run. Effects are sampled from `:templates` (condition multipliers + optional arrival multiplier).

For **GA / lexicase**, treat each `run-week` call as one **evaluation** of a candidate staffing map under a given scenario EDN.
