(ns er-staffing.main
  (:require [er-staffing.scenario-v2 :as scen]
            [er-staffing.simulation.stage-week :as week]
            [er-staffing.evaluation.fitness :as fitness]))

(defn demo
  []
  (let [scenario (scen/load-week-scenario)
        staffing {:rt 1 :cardio 1 :radio 1 :mh 1 :physician 2 :pa 2 :nurse 4}
        summary (week/run-week scenario staffing {:replications 5 :seed 42})
        score (fitness/score-summary scenario staffing summary)]
    (println "Scenario:" (:scenario-id scenario))
    (println "Staffing:" staffing)
    (println "Within daily budget?:" (:within-budget? summary))
    (println "Avg wait:" (:avg-wait-time summary))
    (println "Specialty match rate:" (:specialty-matching-rate summary))
    (println "Fitness (lower better):" score)))

(defn -main
  [& _]
  (demo))

(comment
  "================================================================================
  FILE: main.clj
  NAMESPACE: er-staffing.main

  PURPOSE
    Minimal **demo** (`clojure -M -m er-staffing.main`): load default week scenario,
    fixed small staffing map, run one `run-week` with fixed seed, print budget flag,
    waits, match rate, scalar fitness. For smoke tests — not the GA pipeline.

  INPUTS
    Implicit: data/synthetic/v2/week_scenario.edn via scenario-v2 loader.

  OUTPUTS
    Printed lines only.

  SEE ALSO
    experiments.presentation-run for full comparisons and CSVs.
  ================================================================================")
