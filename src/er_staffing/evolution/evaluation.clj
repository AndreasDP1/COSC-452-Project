(ns er-staffing.evolution.evaluation
  (:require [er-staffing.evaluation.fitness :as fitness]
            [er-staffing.simulation.stage-week :as week]))

(defn case-errors
  "One scalar error per scenario (lower better): fitness/score-summary on run-week."
  [scenarios staffing opts]
  (mapv (fn [sc]
          (let [summary (week/run-week sc staffing opts)]
            (fitness/score-summary sc staffing summary)))
        scenarios))

(defn mean-error
  [errs]
  (/ (reduce + errs) (double (count errs))))

(comment
  "================================================================================
  FILE: evolution/evaluation.clj
  NAMESPACE: er-staffing.evolution.evaluation

  PURPOSE
    Connect GA to simulation: for each scenario, run `stage-week/run-week`, convert
    summary to scalar error via `evaluation.fitness/score-summary`. Produces one
    number per scenario -> error vector for lexicase.

  INPUTS
    scenarios, staffing map, opts {:replications :seed}.

  OUTPUTS
    Vector of errors; mean-error averages them for sorting elites.
  ================================================================================")
