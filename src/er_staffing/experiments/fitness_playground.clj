(ns er-staffing.experiments.fitness-playground
  (:require [er-staffing.params :as params]
            [er-staffing.simulation.stage-a :as sim]
            [er-staffing.evaluation.fitness :as fitness]
            [er-staffing.evaluation.lexicase :as lex]))

(def sample-staffing
  [{:nurses 2 :pit-physicians 0}
   {:nurses 3 :pit-physicians 0}
   {:nurses 3 :pit-physicians 1}
   {:nurses 4 :pit-physicians 1}])

(defn compare-staffing
  [scenario-path]
  (let [scenario (params/load-scenario scenario-path)
        summaries
        (for [staffing sample-staffing]
          (let [summary (sim/run-shift scenario staffing {:replications 10 :seed 42})]
            {:staffing staffing
             :score (fitness/score-summary scenario staffing summary)
             :cases (lex/summary->case-errors summary)}))]
    summaries))
