(ns er-staffing.evaluation.fitness
  "Scalar fitness for V2 week summaries (`run-week` / `run-one-week`)."
  (:require [er-staffing.staffing-v2 :as sv2]))

(def default-weights
  {:wait-time 0.25
   :time-in-system 0.25
   :queue-length 0.15
   :specialty-mismatch 0.25
   :budget-penalty 10000.0})

(defn score-summary
  "Lower is better. Uses daily staffing cost vs :daily-budget-cap in scenario."
  ([scenario staffing summary]
   (score-summary scenario staffing summary default-weights))
  ([scenario staffing summary weights]
   (let [cost (sv2/daily-staffing-cost scenario staffing)
         cap (double (:daily-budget-cap scenario))
         over? (> cost cap)
         penalty (if over? (:budget-penalty weights) 0.0)
         match-rate (double (or (:specialty-matching-rate summary) 0.0))
         mismatch-term (- 1.0 match-rate)]
     (+ (* (:wait-time weights) (double (:avg-wait-time summary)))
        (* (:time-in-system weights) (double (:avg-time-in-system summary)))
        (* (:queue-length weights) (double (:avg-queue-length summary)))
        (* (:specialty-mismatch weights) mismatch-term)
        penalty))))

(comment
  "================================================================================
  FILE: evaluation/fitness.clj
  NAMESPACE: er-staffing.evaluation.fitness

  PURPOSE
    Scalar **fitness** from one week simulation summary: weighted sum of avg wait,
    time-in-system, queue length, specialty mismatch, plus large penalty if daily
    staffing cost exceeds scenario :daily-budget-cap.

  INPUTS
    scenario (for cap and costs), staffing map, summary from run-week.

  OUTPUTS
    Double; lower better. Used as GA objective per scenario.
  ================================================================================")
