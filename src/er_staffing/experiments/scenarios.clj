(ns er-staffing.experiments.scenarios
  (:require [er-staffing.scenario-v2 :as scen]))

(defn week-v1
  []
  (scen/load-week-scenario))

(defn week-v1-real-hourly
  []
  (scen/load-week-scenario-with-real-hourly-profile))

(def ^:private week-v1-pres-path "data/synthetic/v2/week_scenario_presentation.edn")

(def ^:private default-real-hourly-csv "data/processed/real/hourly_arrival_profile_from_sample.csv")

(defn week-v1-for-presentation
  "Higher λ + tighter daily cap than `week-v1`; used by `clojure -M:pres`."
  []
  (scen/load-week-scenario week-v1-pres-path))

(defn week-v1-real-hourly-for-presentation
  []
  (scen/load-week-scenario-with-real-hourly-profile week-v1-pres-path default-real-hourly-csv))

(defn week-severe-respiratory
  []
  (scen/load-week-scenario "data/synthetic/v2/week_case_severe_respiratory.edn"))

(defn week-tight-budget
  []
  (scen/load-week-scenario "data/synthetic/v2/week_case_tight_budget.edn"))

(defn week-high-demand
  []
  (scen/load-week-scenario "data/synthetic/v2/week_case_high_demand.edn"))

(defn with-random-events
  [base]
  (-> base
      (dissoc :events)
      (assoc :random-events
             {:n 5
              :duration-min 5
              :duration-max 16
              :templates [{:condition-multipliers {:respiratory 2.0} :arrival-multiplier 1.0}
                          {:condition-multipliers {:trauma 2.5 :respiratory 1.2} :arrival-multiplier 1.05}
                          {:condition-multipliers {:psych 2.0} :arrival-multiplier 1.0}
                          {:condition-multipliers {:cardio 1.6} :arrival-multiplier 1.0}]})))

(defn lexicase-five-vector
  "Order matches evolution report: baseline -> real hourly -> severe resp -> tight budget -> high demand."
  []
  [(week-v1)
   (week-v1-real-hourly)
   (week-severe-respiratory)
   (week-tight-budget)
   (week-high-demand)])

(defn lexicase-five-vector-for-presentation
  "Same as `lexicase-five-vector`, but baseline + real-hourly use `week_scenario_presentation.edn`."
  []
  [(week-v1-for-presentation)
   (week-v1-real-hourly-for-presentation)
   (week-severe-respiratory)
   (week-tight-budget)
   (week-high-demand)])

(comment
  "================================================================================
  FILE: experiments/scenarios.clj
  NAMESPACE: er-staffing.experiments.scenarios

  PURPOSE
    Named helpers that return **scenario maps** (EDN loaded via scenario-v2) for
    experiments: baseline week-v1, real-hourly-shaped arrivals, stress cases
    (severe respiratory, tight budget, high demand), random-events variant, and the
    fixed vector used by 5-case lexicase.

  INPUTS
    Paths to EDN files under data/synthetic/v2/ (see each defn).

  OUTPUTS
    Clojure maps suitable for `simulation.stage-week/run-week` and for passing as
    the `scenarios` vector to `evolution.ga/run-ga`.

  USED BY
    evolution_run, presentation_run, tests.
  ================================================================================")
